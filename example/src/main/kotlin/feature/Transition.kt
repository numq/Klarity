package feature

data class Transition<out State, out Event>(
    val state: State,
    val events: List<Event> = emptyList(),
)

fun <State, Event> Transition<State, Event>.mergeEvents(vararg event: Event) = copy(
    events = event.asList().plus(this.events)
)

fun <State, Event> Transition<State, Event>.mergeEvents(events: List<Event>) = copy(
    events = events.plus(this.events)
)