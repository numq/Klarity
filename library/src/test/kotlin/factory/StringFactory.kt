package factory

import io.github.numq.klarity.factory.Factory

internal class StringFactory : Factory<Int, String> {
    override fun create(parameters: Int): Result<String> {
        return if (parameters > 0) {
            Result.success("Positive number: $parameters")
        } else {
            Result.failure(IllegalArgumentException("Negative number or zero: $parameters"))
        }
    }
}