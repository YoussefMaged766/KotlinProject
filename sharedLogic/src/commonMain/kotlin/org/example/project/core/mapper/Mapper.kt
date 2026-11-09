package org.example.project.core.mapper

/** One boundary, one class. Same contract as EZeats' core-common Mapper. */
interface Mapper<in Input, out Output> {
    fun map(input: Input): Output
}

interface ListMapper<in Input, out Output> {
    fun map(input: List<Input>): List<Output>
}
