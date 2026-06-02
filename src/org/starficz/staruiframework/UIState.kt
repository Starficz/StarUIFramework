package org.starficz.staruiframework

class UIState<T>(initialValue: T) {
    var value: T = initialValue
        set(newValue) {
            // Only trigger updates if the value actually changed to prevent infinite loops
            if (field != newValue) {
                field = newValue
                observers.forEach { it(newValue) }
            }
        }

    private val observers = mutableListOf<(T) -> Unit>()

    fun onChange(observer: (T) -> Unit) {
        observers.add(observer)
    }

    fun removeObserver(observer: (T) -> Unit) {
        observers.remove(observer)
    }
}