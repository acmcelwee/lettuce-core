/*
 * Copyright 2011-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.lambdaworks.redis.dynamic;

import com.lambdaworks.redis.codec.RedisCodec;
import com.lambdaworks.redis.dynamic.CodecAwareMethodParametersAccessor.TypeContext;
import com.lambdaworks.redis.dynamic.output.CommandOutputFactory;
import com.lambdaworks.redis.dynamic.output.CommandOutputFactoryResolver;
import com.lambdaworks.redis.dynamic.output.OutputSelector;
import com.lambdaworks.redis.dynamic.parameter.ExecutionSpecificParameters;
import com.lambdaworks.redis.dynamic.parameter.MethodParametersAccessor;
import com.lambdaworks.redis.dynamic.segment.CommandSegments;
import com.lambdaworks.redis.output.CommandOutput;
import com.lambdaworks.redis.protocol.Command;
import com.lambdaworks.redis.protocol.CommandArgs;
import com.lambdaworks.redis.protocol.RedisCommand;

/**
 * {@link CommandFactory} based on {@link CommandSegments}.
 *
 * @author Mark Paluch
 * @since 5.0
 */
class CommandSegmentCommandFactory implements CommandFactory {

    private final CommandMethod commandMethod;
    private final CommandSegments segments;
    private final CommandOutputFactoryResolver outputResolver;
    private final RedisCodec<Object, Object> redisCodec;
    private final ParameterBinder parameterBinder = new ParameterBinder();
    private final CommandOutputFactory outputFactory;
    private final TypeContext typeContext;

    public CommandSegmentCommandFactory(CommandSegments commandSegments, CommandMethod commandMethod,
            RedisCodec<?, ?> redisCodec, CommandOutputFactoryResolver outputResolver) {

        this.segments = commandSegments;
        this.commandMethod = commandMethod;
        this.redisCodec = (RedisCodec) redisCodec;
        this.outputResolver = outputResolver;
        this.typeContext = new TypeContext(redisCodec);

        OutputSelector outputSelector = new OutputSelector(commandMethod.getActualReturnType(), redisCodec);
        CommandOutputFactory factory = resolveCommandOutputFactory(outputSelector);

        if (factory == null) {
            throw new IllegalArgumentException(String.format("Cannot resolve CommandOutput for result type %s on method %s",
                    commandMethod.getActualReturnType(), commandMethod.getMethod()));
        }

        if (commandMethod.getParameters() instanceof ExecutionSpecificParameters) {

            ExecutionSpecificParameters executionAwareParameters = (ExecutionSpecificParameters) commandMethod.getParameters();

            if (commandMethod.isFutureExecution() && executionAwareParameters.hasTimeoutIndex()) {
                throw new CommandCreationException(commandMethod,
                        "Asynchronous command methods do not support Timeout parameters");
            }
        }

        this.outputFactory = factory;
    }

    protected CommandOutputFactoryResolver getOutputResolver() {
        return outputResolver;
    }

    protected CommandOutputFactory resolveCommandOutputFactory(OutputSelector outputSelector) {
        return outputResolver.resolveCommandOutput(outputSelector);
    }

    @Override
    public RedisCommand<Object, Object, Object> createCommand(Object[] parameters) {

        MethodParametersAccessor parametersAccessor = new CodecAwareMethodParametersAccessor(
                new DefaultMethodParametersAccessor(commandMethod.getParameters(), parameters), typeContext);

        CommandArgs<Object, Object> args = new CommandArgs<>(redisCodec);

        CommandOutput<Object, Object, ?> output = outputFactory.create(redisCodec);
        Command<Object, Object, ?> command = new com.lambdaworks.redis.protocol.Command<>(this.segments.getCommandType(),
                output, args);

        parameterBinder.bind(args, redisCodec, segments, parametersAccessor);

        return (Command) command;
    }
}
