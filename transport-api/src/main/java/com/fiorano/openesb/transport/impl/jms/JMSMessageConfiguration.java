/*
 * Copyright (c) Fiorano Software Pte. Ltd. and affiliates. All rights reserved. http://www.fiorano.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package com.fiorano.openesb.transport.impl.jms;

import com.fiorano.openesb.transport.MessageConfiguration;

public class JMSMessageConfiguration implements MessageConfiguration {
    public MessageType getType() {
        return type;
    }

    public enum MessageType {
        Bytes, Text , Object , Stream;
    }
    private MessageType type;
    public JMSMessageConfiguration(MessageType type) {
        this.type = type;
    }
}
