package io.github.numq.example.usecase

interface UseCase<in Input, out Output> {
    suspend fun execute(input: Input): Result<Output>
}