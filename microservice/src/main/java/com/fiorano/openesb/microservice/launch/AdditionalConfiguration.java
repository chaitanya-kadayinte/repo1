/*
 * Copyright (c) Fiorano Software Pte. Ltd. and affiliates. All rights reserved. http://www.fiorano.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package com.fiorano.openesb.microservice.launch;

public interface AdditionalConfiguration {
    public String getSchemaRepoPath() ;

    public String getJettyUrl();

    public String getJettySSLUrl();

    public String getCompRepoPath();

    public String getProviderUrl();

    public String getICF();
}
