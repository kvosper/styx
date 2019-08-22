/*
  Copyright (C) 2013-2019 Expedia Inc.

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
 */
package com.hotels.styx.common;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * A general-purpose state-machine.
 *
 * @param <S> state type
 */
public final class StateMachine<S> {
    private final Map<Key<S>, Function<Object, S>> transitions;
    private final BiFunction<S, Object, S> inappropriateEventHandler;
    private final StateChangeListener<S> stateChangeListener;

    private volatile S currentState;

    StateMachine(S initialState, Map<Key<S>, Function<Object, S>> transitions, BiFunction<S, Object, S> inappropriateEventHandler, StateChangeListener<S> stateChangeListener) {
        this.transitions = transitions;
        this.inappropriateEventHandler = inappropriateEventHandler;
        this.stateChangeListener = stateChangeListener;
        this.currentState = initialState;
    }

    /**
     * Returns the current state.
     *
     * @return current state
     */
    public S currentState() {
        return currentState;
    }

    /**
     * Handles an event by performing the a state transition and side-effects associated with the event's type.
     *
     * @param event         an event
     * @param loggingPrefix a prefix to prepend to the beginning of log lines
     */
    public void handle(Object event, String loggingPrefix) {
        S newState = stateMapper(event.getClass())
                .orElse(this::handleInappropriateEvent)
                .apply(event);

        S oldState = currentState;
        currentState = newState;

        stateChangeListener.onStateChange(oldState, currentState, event);
    }

    /**
     * Handles an event by performing the a state transition and side-effects associated with the event's type.
     *
     * @param event an event
     */
    public void handle(Object event) {
        this.handle(event, "");
    }

    private S handleInappropriateEvent(Object event) {
        return inappropriateEventHandler.apply(currentState, event);
    }

    private Optional<Function<Object, S>> stateMapper(Class<?> eventClass) {
        Key<S> key = new Key<>(currentState, eventClass);

        return Optional.ofNullable(transitions.get(key));
    }

    static final class Key<S> {
        private final S state;
        private final Class<?> eventClass;

        private Key(S state, Class<?> eventClass) {
            this.state = state;
            this.eventClass = eventClass;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Key<?> key = (Key<?>) o;
            return Objects.equals(state, key.state)
                    && Objects.equals(eventClass, key.eventClass);
        }

        @Override
        public int hashCode() {
            return Objects.hash(state, eventClass);
        }
    }

    static class StateEventHandler<S> {
        private final Key<S> key;
        private final Function<Object, S> mapper;

        <E> StateEventHandler(S state, Class<E> eventClass, Function<E, S> mapper) {
            this.key = new Key<>(state, eventClass);
            this.mapper = event -> mapper.apply((E) event);
        }

        Key<S> key() {
            return key;
        }

        Function<Object, S> mapper() {
            return mapper;
        }
    }
}
