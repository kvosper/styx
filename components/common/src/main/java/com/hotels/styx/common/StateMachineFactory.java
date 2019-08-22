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

import com.hotels.styx.common.StateMachine.Key;
import com.hotels.styx.common.StateMachine.StateEventHandler;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toMap;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * A general-purpose state-machine factory.
 *
 * @param <S> state type
 */
public final class StateMachineFactory<S> {
    private final Map<Key<S>, Function<Object, S>> transitions;
    private final BiFunction<S, Object, S> inappropriateEventHandler;
    private final StateChangeListener<S> stateChangeListener;

    private final S initialState;

    private StateMachineFactory(
            S initialState,
            List<StateEventHandler<S>> handlers,
            BiFunction<S, Object, S> inappropriateEventHandler,
            StateChangeListener<S> stateChangeListener) {
        this.initialState = requireNonNull(initialState);
        this.transitions = handlers.stream().collect(toMap(
                StateEventHandler::key,
                StateEventHandler::mapper));
        this.inappropriateEventHandler = requireNonNull(inappropriateEventHandler);
        this.stateChangeListener = requireNonNull(stateChangeListener);
    }

    public StateMachine<S> newStateMachine() {
        return new StateMachine<S>(initialState, transitions, inappropriateEventHandler, stateChangeListener);
    }

    /**
     * StateMachine builder.
     *
     * @param <S> state type
     */
    public static final class Builder<S> {
        private final List<StateEventHandler<S>> stateEventHandlers = new ArrayList<>();
        private BiFunction<S, Object, S> inappropriateEventHandler;
        private S initialState;
        private StateChangeListener<S> stateChangeListener = (oldState, newState, event) -> {
        };

        /**
         * Sets the state that the state-machine should start in.
         *
         * @param initialState initial state
         * @return this builder
         */
        public Builder<S> initialState(S initialState) {
            this.initialState = initialState;
            return this;
        }

        /**
         * Associates a state and event type with a function that returns a new state and possibly side-effects.
         *
         * @param state      state to transition from
         * @param eventClass event class
         * @param mapper     function that returns the new state
         * @param <E>        event type
         * @return this builder
         */
        public <E> Builder<S> transition(S state, Class<E> eventClass, Function<E, S> mapper) {
            this.stateEventHandlers.add(new StateEventHandler<>(state, eventClass, mapper));
            return this;
        }

        /**
         * Determines how to handle an inappropriate event. That is, an event that has no transition associated with the current state.
         *
         * @param mapper function that returns the new state
         * @param <E>    event type
         * @return this builder
         */
        public <E> Builder<S> onInappropriateEvent(BiFunction<S, E, S> mapper) {
            this.inappropriateEventHandler = (state, event) -> mapper.apply(state, (E) event);
            return this;
        }

        /**
         * Add state-change-listener to be informed about state changes, including due to inappropriate events.
         *
         * @param stateChangeListener state-change-listener
         * @return this builder
         */
        public Builder<S> onStateChange(StateChangeListener<S> stateChangeListener) {
            this.stateChangeListener = requireNonNull(stateChangeListener);
            return this;
        }

        /**
         * Builds a new state-machine with on the configuration provided to this builder.
         *
         * @return a new state-machine
         */
        public StateMachineFactory<S> build() {
            return new StateMachineFactory<>(initialState, stateEventHandlers, inappropriateEventHandler, stateChangeListener);
        }

        public Builder<S> debugTransitions(String messagePrefix) {
            Logger logger = getLogger(StateMachine.class);

            return this.onStateChange((oldState, newState, event)-> {
                logger.info("{} {}: {} -> {}", new Object[] {messagePrefix, event, oldState, newState});
            });
        }
    }
}
