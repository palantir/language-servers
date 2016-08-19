/*
 * Copyright 2016 Palantir Technologies, Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.palantir.ls.util;

import io.typefox.lsapi.Message;
import io.typefox.lsapi.NotificationMessage;
import io.typefox.lsapi.RequestMessage;
import io.typefox.lsapi.ResponseMessage;
import io.typefox.lsapi.services.transport.trace.MessageTracer;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.slf4j.Logger;

public class LoggerMessageTracer implements MessageTracer {

    private final Logger logger;

    public LoggerMessageTracer(Logger logger) {
        this.logger = logger;
    }

    @Override
    public void onError(String message, Throwable throwable) {
        if (message != null) {
            logger.error(message);
        }

        if (throwable != null) {
            StringWriter stringWriter = new StringWriter();
            PrintWriter printWriter = new PrintWriter(stringWriter);
            throwable.printStackTrace(printWriter);
            String stackTrace = stringWriter.toString();
            logger.error(stackTrace);
        }
    }

    @Override
    public void onRead(Message message, String json) {
        if (message instanceof RequestMessage) {
            logger.info("Client Request:\n\t" + json);
        } else if (message instanceof NotificationMessage) {
            logger.info("Client Notification:\n\t" + json);
        } else {
            logger.info("Client Sent:\n\t" + json);
        }
    }

    @Override
    public void onWrite(Message message, String json) {
        if (message instanceof ResponseMessage) {
            logger.info("Server Response:\n\t" + json);
        } else if (message instanceof NotificationMessage) {
            logger.info("Server Notification:\n\t" + json);
        } else {
            logger.info("Server Sent:\n\t" + json);
        }
    }

}
