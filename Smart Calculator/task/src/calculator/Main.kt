package calculator

import java.math.BigInteger
import java.util.*

fun main() {
    val scanner = Scanner(System.`in`)
    val calculator = Calculator()
    while (!calculator.closed) {
        val nextLine = scanner.nextLine()
        try {
            calculator.process(nextLine)
        } catch (e: Calculator.InvalidIdentifierException) {
            println("Invalid identifier")
        } catch (e: Calculator.InvalidExpressionException) {
            println("Invalid expression")
        } catch (e: Calculator.InvalidAssignmentException) {
            println("Invalid assignment")
        } catch (e: Calculator.UnknownVariableException) {
            println("Unknown variable")
        } catch (e: Exception) {
            println(e)
            e.printStackTrace()
        }
    }
}

class Calculator {
    var closed = false
    private val context: MutableMap<String, BigInteger> = mutableMapOf()

    fun process(nextLine: String?) {
        when (recogniseAction(nextLine)) {
            Action.COMMAND -> performAction(nextLine!!)
            Action.ASSIGNMENT -> addVariableToContext(nextLine!!)
            Action.CALCULATION -> printResult(nextLine!!)
        }
    }

    private fun performAction(action: String) {
        when (action) {
            "/exit" -> {
                println("Bye!")
                closed = true
            }
            "/help" -> println("Help description")
            else -> println("Unknown command")
        }
    }

    private fun addVariableToContext(nextLine: String) {
        val assignment = nextLine.replace(" ", "").split("=")
        context[assignment[0]] = resolveValue(assignment[1])
    }

    private fun printResult(nextLine: String) {
        val infixNotation = splitStatement(nextLine)
        val postfixNotation = convertToPostfix(infixNotation)
        println(calculateResult(postfixNotation))
    }

    private fun splitStatement(statement: String): Stack<String> {
        val result = Stack<String>()
        val chars = statement.replace(" ", "").toCharArray()
        var state = getNextState(chars.first()) // 0-init, 1-number, 2-operator, 3-brackets
        val tmp = mutableListOf<Char>()
        for (char in chars) {
            if (char == '(' || char == ')') {
                result.push(sanitize(String(tmp.toCharArray())))
                tmp.clear()
                state = 0
                tmp.add(char)
                continue
            }
            val nextState = getNextState(char)
            if (nextState != state) { // new operand starts
                result.push(sanitize(String(tmp.toCharArray())))
                tmp.clear()
            }
            tmp.add(char)
            state = nextState
        }
        result.push(String(tmp.toCharArray()))
        return result
    }

    private fun sanitize(string: String): String {
        if (string.matches("[-+]+".toRegex())) {
            return when (string.count { it == '-' } % 2) {
                0 -> "+"
                else -> "-"
            }
        }
        if (string.matches("[*/^]{2,}".toRegex())) {
            throw InvalidExpressionException()
        }
        return string
    }

    private fun getNextState(char: Char): Int {
        return when {
            "[a-zA-Z0-9]".toRegex().matches(char.toString()) -> 1
            "[-+*/^]".toRegex().matches(char.toString()) -> 2
            "[()]".toRegex().matches(char.toString()) -> 3
            else -> throw InvalidExpressionException()
        }
    }

    private fun convertToPostfix(infixNotation: Stack<String>): Stack<String> {
        val tmp = Stack<String>()
        val result = Stack<String>()
        for (item in infixNotation) {
            when (recogniseItem(item)) {
                "value" -> result.push(item)
                "operator" -> handleOperator(item, tmp, result)
                "leftBracket" -> tmp.push(item)
                "rightBracket" -> popBracket(tmp, result)
            }
        }
        while (tmp.isNotEmpty()) result.push(tmp.pop())
        if (result.contains("(")) {
            throw InvalidExpressionException()
        }
        return result
    }

    private fun popBracket(tmp: Stack<String>, result: Stack<String>) {
        while (tmp.peek() != "(") {
            result.push(tmp.pop())
            if (tmp.isEmpty()) throw InvalidExpressionException()
        }
        tmp.pop()
    }

    private fun recogniseItem(item: String): String {
        return when {
            item.matches("[-+*/^]".toRegex()) -> "operator"
            item == "(" -> "leftBracket"
            item == ")" -> "rightBracket"
            else -> "value"
        }
    }

    private fun handleOperator(item: String, tmp: Stack<String>, result: Stack<String>) {
        if (tmp.isNotEmpty() && tmp.peek() != "(" && Operator.from(tmp.peek()).hasPriorityOver(Operator.from(item))) {
            result.push(tmp.pop())
        }
        tmp.push(item)
    }

    private fun calculateResult(parts: Stack<String>): BigInteger {
        val tmp = Stack<String>()
        for (part in parts) {
            when (recogniseItem(part)) {
                "value" -> tmp.push(part)
                "operator" -> evaluate(part, tmp)
            }
        }
        return resolveValue(tmp.pop())
    }

    private fun evaluate(item: String, tmp: Stack<String>) {
        val b = resolveValue(tmp.pop())
        val a = resolveValue(tmp.pop())
        tmp.push(Operator.from(item).calculate(a, b).toString())
    }

    private fun resolveValue(part: String): BigInteger {
        if (part.matches("[+-]?[0-9]+".toRegex())) {
            return BigInteger(part)
        }
        return context.getOrElse(part) { throw UnknownVariableException() }
    }

    private fun recogniseAction(command: String?): Action {
        if (command == null || command.isEmpty()) {
            return Action.EMPTY_ACTION
        }
        if (command.matches("/.*".toRegex())) {
            return Action.COMMAND
        }
        if (command.matches(".*=.*".toRegex())) {
            val split = command.split("=")
            if (!split[0].trim().matches("[a-zA-Z]+".toRegex())) {
                throw InvalidIdentifierException()
            }
            if (!split[1].trim().matches("[a-zA-Z]+|-?[0-9]+".toRegex())) {
                throw InvalidAssignmentException()
            }
            return Action.ASSIGNMENT
        }
        return Action.CALCULATION
    }

    enum class Action {
        COMMAND,
        ASSIGNMENT,
        CALCULATION,
        EMPTY_ACTION
    }

    class InvalidIdentifierException : Exception()
    class InvalidAssignmentException : Exception()
    class UnknownVariableException : Exception()
    class InvalidExpressionException : Exception()
}

@Suppress("unused")
enum class Operator(private val sign: String, private val action: (BigInteger, BigInteger) -> BigInteger, private val priority: Int) {
    ADDITION("+", { a, b -> a + b }, 1),
    SUBTRACTION("-", { a, b -> a - b }, 1),
    MULTIPLICATION("*", { a, b -> a * b }, 2),
    DIVISION("/", { a, b -> a / b }, 2),
    POWER("^", { a, b -> a.pow(b.toInt()) }, 3);

    companion object {
        fun from(string: String): Operator {
            return values().filter { it.sign == string }
                    .getOrElse(0) { println(string); throw Calculator.InvalidExpressionException() }
        }
    }

    fun hasPriorityOver(op: Operator): Boolean {
        return this.priority >= op.priority
    }

    fun calculate(a: BigInteger, b: BigInteger): BigInteger {
        return action.invoke(a, b)
    }
}
