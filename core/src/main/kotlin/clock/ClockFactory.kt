package clock

import factory.Factory

object ClockFactory : Factory<ClockFactory.Parameters, Clock> {
    object Parameters

    override fun create(parameters: Parameters) = Clock.create()
}