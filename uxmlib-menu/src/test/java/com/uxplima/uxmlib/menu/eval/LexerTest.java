package com.uxplima.uxmlib.menu.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * The first line of the expression sandbox. The lexer recognises a fixed vocabulary and refuses every character
 * outside it, so the tests that matter most here are the refusals: a character that has no token is what stops an
 * expression from ever spelling a method call, a field access, or a bitwise trick.
 *
 * <p>The lexer knows nothing about which functions exist or which shapes are legal. It is happy to hand back tokens
 * that the parser then rejects, and pinning that split keeps a later reader from moving an allow-list down here.
 */
class LexerTest {

    private static List<Token> lex(String source) throws ExpressionException {
        return new Lexer(source).tokenize();
    }

    private static List<Token.Kind> kinds(String source) throws ExpressionException {
        return lex(source).stream().map(Token::kind).toList();
    }

    // -- the shape of a token list --------------------------------------------------------------------------

    @Test
    void everyTokenListEndsWithAnEndTokenIncludingAnEmptyOne() throws ExpressionException {
        assertThat(kinds("")).containsExactly(Token.Kind.END);
        assertThat(kinds("   \t  "))
                .as("whitespace alone is still an empty expression")
                .containsExactly(Token.Kind.END);
    }

    @Test
    void whitespaceSeparatesTokensAndIsOtherwiseDropped() throws ExpressionException {
        assertThat(kinds("1+2")).isEqualTo(kinds("  1  +  2  "));
    }

    /** A number carries its value in its own slot, so nothing downstream has to parse the text a second time. */
    @Test
    void aNumberCarriesItsValueRatherThanItsSpelling() throws ExpressionException {
        Token number = lex("2.50").get(0);
        assertThat(number.kind()).isEqualTo(Token.Kind.NUMBER);
        assertThat(number.number()).isEqualTo(2.5d);
        assertThat(number.text()).isEmpty();
    }

    @Test
    void parenthesesAndCommasAreTheirOwnKindsRatherThanOperators() throws ExpressionException {
        assertThat(kinds("min(1,2)"))
                .containsExactly(
                        Token.Kind.IDENT,
                        Token.Kind.LPAREN,
                        Token.Kind.NUMBER,
                        Token.Kind.COMMA,
                        Token.Kind.NUMBER,
                        Token.Kind.RPAREN,
                        Token.Kind.END);
    }

    // -- the vocabulary is closed ---------------------------------------------------------------------------

    @Test
    void aCharacterOutsideTheVocabularyIsNamedRatherThanSkipped() {
        assertThatThrownBy(() -> lex("1 # 2"))
                .isInstanceOf(ExpressionException.class)
                .hasMessageContaining("unexpected character: #");
    }

    /**
     * There is no way to spell a member access, so no expression can reach a method or a field of a host object.
     * A dot is only ever part of a number, and a dot that is not part of one fails as a malformed number.
     */
    @Test
    void thereIsNoMemberAccessBecauseADotIsOnlyEverPartOfANumber() {
        assertThatThrownBy(() -> lex("."))
                .isInstanceOf(ExpressionException.class)
                .hasMessageContaining("malformed number");
    }

    /** Only the doubled forms are boolean operators, so the single characters have no token and cannot be written. */
    @Test
    void theSingleCharacterBitwiseFormsHaveNoToken() {
        assertThatThrownBy(() -> lex("1 & 2"))
                .isInstanceOf(ExpressionException.class)
                .hasMessageContaining("unexpected character: &");
        assertThatThrownBy(() -> lex("1 | 2"))
                .isInstanceOf(ExpressionException.class)
                .hasMessageContaining("unexpected character: |");
    }

    /** A lone equals sign is not a token, so an expression cannot assign to anything. */
    @Test
    void aLoneEqualsSignIsNotATokenSoNothingCanBeAssigned() {
        assertThatThrownBy(() -> lex("a = 1"))
                .isInstanceOf(ExpressionException.class)
                .hasMessageContaining("unexpected character: =");
    }

    // -- two characters before one --------------------------------------------------------------------------

    @Test
    void aTwoCharacterOperatorIsOneTokenAndNotItsPrefixPlusTheRest() throws ExpressionException {
        for (String operator : List.of("==", "!=", "<=", ">=", "&&", "||")) {
            List<Token> tokens = lex("1 " + operator + " 2");
            assertThat(tokens).as(operator).hasSize(4);
            assertThat(tokens.get(1).kind()).isEqualTo(Token.Kind.OPERATOR);
            assertThat(tokens.get(1).text()).isEqualTo(operator);
        }
    }

    @Test
    void aOneCharacterOperatorIsStillReadWhenNoTwoCharacterFormMatches() throws ExpressionException {
        for (String operator : List.of("+", "-", "*", "/", "%", "^", "<", ">")) {
            List<Token> tokens = lex("1 " + operator + " 2");
            assertThat(tokens.get(1).text()).as(operator).isEqualTo(operator);
        }
    }

    /** A two-character operator at the very end of the input is still matched as one token. */
    @Test
    void aTwoCharacterOperatorIsMatchedEvenWithNothingAfterIt() throws ExpressionException {
        List<Token> tokens = lex("1<=");
        assertThat(tokens.get(1).text()).isEqualTo("<=");
        assertThat(tokens.get(2).kind()).isEqualTo(Token.Kind.END);
    }

    // -- numbers --------------------------------------------------------------------------------------------

    /**
     * A number stops at its second dot rather than failing there. The lexer hands back two numbers and the parser
     * is the one that refuses, which is the same split as an unknown function name: shape here, meaning there.
     */
    @Test
    void aSecondDotEndsTheNumberAndLeavesTheComplaintToTheParser() throws ExpressionException {
        assertThat(kinds("1.2.3")).containsExactly(Token.Kind.NUMBER, Token.Kind.NUMBER, Token.Kind.END);
        assertThat(lex("1.2.3").get(0).number()).isEqualTo(1.2d);
        assertThatThrownBy(() -> Expressions.evaluateNumber("1.2.3")).isInstanceOf(ExpressionException.class);
    }

    /**
     * Every computed value passes the finite check, but a literal reaches no operator on its way out, so a literal
     * larger than a double can hold would be the one route by which a non-finite value could leave the evaluator
     * and land in a menu line as the text "Infinity". The lexer refuses it where the loss happens.
     */
    @Test
    void aLiteralLargerThanANumberCanHoldIsRefusedRatherThanBecomingInfinity() {
        String tooLarge = "9".repeat(400);
        assertThatThrownBy(() -> lex(tooLarge))
                .isInstanceOf(ExpressionException.class)
                .hasMessageContaining("larger than a number can hold");
        assertThatThrownBy(() -> Expressions.evaluateNumber(tooLarge)).isInstanceOf(ExpressionException.class);
    }

    /** A literal too small to represent is a different case: it underflows to zero, as every double does. */
    @Test
    void aLiteralTooSmallToRepresentUnderflowsToZeroRatherThanFailing() throws ExpressionException {
        assertThat(lex("0." + "0".repeat(400) + "1").get(0).number()).isZero();
    }

    @Test
    void aNumberMayOpenOrCloseOnItsDot() throws ExpressionException {
        assertThat(lex(".5").get(0).number()).isEqualTo(0.5d);
        assertThat(lex("5.").get(0).number()).isEqualTo(5d);
    }

    // -- identifiers ----------------------------------------------------------------------------------------

    @Test
    void anIdentifierMayOpenWithAnUnderscoreAndCarryDigits() throws ExpressionException {
        assertThat(lex("_world_2").get(0).text()).isEqualTo("_world_2");
    }

    /** A digit opens a number, so an identifier cannot start with one: the two are read apart, not merged. */
    @Test
    void aDigitOpensANumberEvenWhenLettersFollowIt() throws ExpressionException {
        assertThat(kinds("2x")).containsExactly(Token.Kind.NUMBER, Token.Kind.IDENT, Token.Kind.END);
    }

    /**
     * The lexer holds no allow-list. A name no function has still tokenizes, and the refusal comes from the layer
     * that knows what the names mean.
     */
    @Test
    void aNameNoFunctionHasStillTokenizesBecauseTheLexerHoldsNoAllowList() throws ExpressionException {
        assertThat(kinds("exec(1)"))
                .containsExactly(
                        Token.Kind.IDENT, Token.Kind.LPAREN, Token.Kind.NUMBER, Token.Kind.RPAREN, Token.Kind.END);
        assertThatThrownBy(() -> Expressions.evaluateNumber("exec(1)")).isInstanceOf(ExpressionException.class);
    }

    // -- strings --------------------------------------------------------------------------------------------

    @Test
    void eitherQuoteOpensAStringAndTheOtherOneIsOrdinaryInsideIt() throws ExpressionException {
        assertThat(lex("'the \"quoted\" world'").get(0).text()).isEqualTo("the \"quoted\" world");
        assertThat(lex("\"it's here\"").get(0).text()).isEqualTo("it's here");
    }

    /**
     * There is no escape character. A backslash is an ordinary character and the string ends at the first matching
     * quote, so an operator writing a Windows path in a menu config gets the backslash back untouched.
     */
    @Test
    void aBackslashIsAnOrdinaryCharacterRatherThanAnEscape() throws ExpressionException {
        assertThat(lex("'a\\b'").get(0).text()).isEqualTo("a\\b");
    }

    @Test
    void anEmptyStringLiteralIsAStringAndNotAnError() throws ExpressionException {
        assertThat(kinds("''")).containsExactly(Token.Kind.STRING, Token.Kind.END);
        assertThat(lex("''").get(0).text()).isEmpty();
    }

    @Test
    void aStringThatIsNeverClosedIsNamedRatherThanRunningToTheEnd() {
        assertThatThrownBy(() -> lex("'never closed"))
                .isInstanceOf(ExpressionException.class)
                .hasMessageContaining("unterminated string literal");
    }

    /** A quote closes only on its own kind, so a string opened with one quote is not closed by the other. */
    @Test
    void aStringIsClosedOnlyByTheQuoteThatOpenedIt() {
        assertThatThrownBy(() -> lex("'opened here\""))
                .isInstanceOf(ExpressionException.class)
                .hasMessageContaining("unterminated string literal");
    }
}
