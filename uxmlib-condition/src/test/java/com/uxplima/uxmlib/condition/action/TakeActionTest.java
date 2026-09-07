package com.uxplima.uxmlib.condition.action;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.uxplima.uxmlib.condition.FakeItemStore;
import com.uxplima.uxmlib.condition.FakeWallet;
import com.uxplima.uxmlib.condition.OperandResolver;
import org.junit.jupiter.api.Test;

/**
 * The two take verbs spend through the context's seams and are all or nothing. The tests that matter here are
 * the refusals: a cost that cannot be met must leave the wallet and the store exactly as they were, and must
 * stop the rest of the list rather than letting a reward fire behind an unpaid price.
 */
class TakeActionTest {

    private static OperandResolver mapResolver(Map<String, String> values) {
        return (player, template) -> {
            String result = template;
            for (Map.Entry<String, String> entry : values.entrySet()) {
                result = result.replace(entry.getKey(), entry.getValue());
            }
            return result;
        };
    }

    @Test
    void takeMoneyParsesAnAmountAloneAsTheDefaultCurrency() {
        ParsedAction parsed = ActionParser.parse("[take-money] 100");
        assertThat(parsed.type()).isEqualTo(ActionType.TAKE_MONEY);

        FakeWallet wallet = new FakeWallet("", 250);
        parsed.action().run(context(wallet));

        assertThat(wallet.balance(null, "")).isEqualTo(150);
    }

    @Test
    void takeMoneyParsesACurrencyAndAnAmount() {
        FakeWallet wallet = new FakeWallet("coins", 250);
        ActionParser.parse("[take-money] coins 100").action().run(context(wallet));
        assertThat(wallet.balance(null, "coins")).isEqualTo(150);
    }

    @Test
    void takeItemParsesAnItemAloneAsOne() {
        ParsedAction parsed = ActionParser.parse("[take-item] diamond");
        assertThat(parsed.type()).isEqualTo(ActionType.TAKE_ITEM);

        FakeItemStore store = new FakeItemStore("diamond", 3);
        parsed.action().run(context(store));

        assertThat(store.count(null, "diamond")).isEqualTo(2);
    }

    @Test
    void takeItemParsesAnItemAndAnAmount() {
        FakeItemStore store = new FakeItemStore("diamond", 10);
        ActionParser.parse("[take-item] diamond 4").action().run(context(store));
        assertThat(store.count(null, "diamond")).isEqualTo(6);
    }

    @Test
    void anAmountMayBeAPlaceholderResolvedAtRunTime() {
        FakeItemStore store = new FakeItemStore("diamond", 10);
        ActionContext context = ActionContext.builder(mapResolver(Map.of("%price%", "7")))
                .itemStore(store)
                .build();
        ActionParser.parse("[take-item] diamond %price%").action().run(context);
        assertThat(store.count(null, "diamond")).isEqualTo(3);
    }

    @Test
    void aTakeThatCannotBePaidThrowsAndSpendsNothing() {
        FakeWallet wallet = new FakeWallet("coins", 50);
        Action action = ActionParser.parse("[take-money] coins 100").action();

        assertThatThrownBy(() -> action.run(context(wallet)))
                .isInstanceOf(ActionCostException.class)
                .hasMessageContaining("100")
                .hasMessageContaining("coins");

        assertThat(wallet.balance(null, "coins")).isEqualTo(50);
        assertThat(wallet.withdrawals()).isZero();
    }

    @Test
    void anItemTakeThatCannotBePaidConsumesNothing() {
        FakeItemStore store = new FakeItemStore("diamond", 2);
        Action action = ActionParser.parse("[take-item] diamond 3").action();

        assertThatThrownBy(() -> action.run(context(store))).isInstanceOf(ActionCostException.class);

        assertThat(store.count(null, "diamond")).isEqualTo(2);
        assertThat(store.takes()).isZero();
    }

    @Test
    void theWholeListIsChargedBeforeAnyOfItRuns() {
        FakeWallet wallet = new FakeWallet("coins", 500);
        FakeItemStore store = new FakeItemStore("diamond", 1);
        List<String> commands = new ArrayList<>();
        ActionContext context = ActionContext.builder(OperandResolver.identity())
                .wallet(wallet)
                .itemStore(store)
                .consoleSink(commands::add)
                .build();

        ActionList list = ActionList.parse(
                List.of("[take-money] coins 100", "[take-item] diamond 3", "[console] give Steve diamond"));

        assertThatThrownBy(() -> list.run(context)).isInstanceOf(ActionCostException.class);

        // The money must still be there: the item cost was unpayable, so nothing was taken and no reward ran.
        assertThat(wallet.balance(null, "coins")).isEqualTo(500);
        assertThat(wallet.withdrawals()).isZero();
        assertThat(store.count(null, "diamond")).isEqualTo(1);
        assertThat(commands).isEmpty();
    }

    @Test
    void aPayableListTakesEverythingAndRunsTheReward() {
        FakeWallet wallet = new FakeWallet("coins", 500);
        FakeItemStore store = new FakeItemStore("diamond", 8);
        List<String> commands = new ArrayList<>();
        ActionContext context = ActionContext.builder(OperandResolver.identity())
                .wallet(wallet)
                .itemStore(store)
                .consoleSink(commands::add)
                .build();

        ActionList list = ActionList.parse(
                List.of("[take-money] coins 100", "[take-item] diamond 3", "[console] give Steve diamond"));
        list.run(context);

        assertThat(wallet.balance(null, "coins")).isEqualTo(400);
        assertThat(store.count(null, "diamond")).isEqualTo(5);
        assertThat(commands).containsExactly("give Steve diamond");
    }

    @Test
    void affordableReadsWithoutSpending() {
        FakeWallet wallet = new FakeWallet("coins", 500);
        ActionList list = ActionList.parse(List.of("[take-money] coins 100"));
        ActionContext context = context(wallet);

        assertThat(list.affordable(context)).isTrue();
        assertThat(list.affordable(context)).isTrue();
        assertThat(wallet.balance(null, "coins")).isEqualTo(500);
        assertThat(wallet.withdrawals()).isZero();
    }

    @Test
    void affordableIsFalseWhenACostCannotBeMet() {
        ActionList list = ActionList.parse(List.of("[take-money] coins 100"));
        assertThat(list.affordable(context(new FakeWallet("coins", 99)))).isFalse();
    }

    @Test
    void theCostActionsAreReportedInDeclarationOrder() {
        ActionList list = ActionList.parse(
                List.of("[message] hello", "[take-money] coins 1", "[take-item] diamond 1", "[close]"));
        assertThat(list.costActions()).hasSize(2);
    }

    @Test
    void anUnwiredContextRefusesEveryTakeRatherThanGrantingItFree() {
        ActionContext context =
                ActionContext.builder(OperandResolver.identity()).build();
        assertThatThrownBy(() -> ActionParser.parse("[take-money] 1").action().run(context))
                .isInstanceOf(ActionCostException.class);
        assertThatThrownBy(
                        () -> ActionParser.parse("[take-item] diamond").action().run(context))
                .isInstanceOf(ActionCostException.class);
    }

    @Test
    void aTakeThatDoesNotResolveToANumberRefusesRatherThanTakingZero() {
        FakeWallet wallet = new FakeWallet("coins", 500);
        Action action = ActionParser.parse("[take-money] coins %price%").action();
        assertThatThrownBy(() -> action.run(context(wallet))).isInstanceOf(ActionCostException.class);
        assertThat(wallet.withdrawals()).isZero();
    }

    @Test
    void aLiteralAmountIsCheckedAtLoad() {
        assertThatThrownBy(() -> ActionParser.parse("[take-money] coins nine"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a number");
        assertThatThrownBy(() -> ActionParser.parse("[take-item] diamond 0"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("above zero");
        assertThatThrownBy(() -> ActionParser.parse("[take-item] diamond -1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("above zero");
    }

    @Test
    void tooManyTokensIsALoadError() {
        assertThatThrownBy(() -> ActionParser.parse("[take-money] coins 10 extra"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("take-money");
        assertThatThrownBy(() -> ActionParser.parse("[take-item] diamond 10 extra"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("take-item");
    }

    @Test
    void aTakeWithNoPayloadIsALoadError() {
        assertThatThrownBy(() -> ActionParser.parse("[take-money]"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("needs a payload");
        assertThatThrownBy(() -> ActionParser.parse("[take-item]"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("needs a payload");
    }

    @Test
    void takeActionsRunOnTheOwningThread() {
        assertThat(ActionParser.parse("[take-money] 1").action().async()).isFalse();
        assertThat(ActionParser.parse("[take-item] diamond").action().async()).isFalse();
    }

    private static ActionContext context(FakeWallet wallet) {
        return ActionContext.builder(OperandResolver.identity()).wallet(wallet).build();
    }

    private static ActionContext context(FakeItemStore store) {
        return ActionContext.builder(OperandResolver.identity())
                .itemStore(store)
                .build();
    }
}
