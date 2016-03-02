/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.fiorano.openesb.rmiconnector;

import com.fiorano.openesb.rmiconnector.api.IRmiManager;
import com.fiorano.openesb.rmiconnector.api.ServiceException;
import com.fiorano.openesb.rmiconnector.connector.RmiConnector;
import com.fiorano.openesb.rmiconnector.impl.RmiManager;
import com.fiorano.openesb.utils.exception.FioranoException;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;

import java.rmi.AccessException;
import java.rmi.AlreadyBoundException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

public class Activator implements BundleActivator {

    public void start(BundleContext context) {
        System.out.println("Starting the bundle- RMI Connector");
        System.setProperty("java.rmi.server.hostname", "192.168.2.214");
        Thread.currentThread().setContextClassLoader(
                this.getClass().getClassLoader());
        RmiConnector rmiConnector = new RmiConnector();
        try {
            rmiConnector.createService();
        } catch (FioranoException e) {
            e.printStackTrace();
        }
        IRmiManager rmiManagerStub = null;
        Registry registry;
        IRmiManager rmiManager = null;
        try {
            rmiManager = new RmiManager(context, 2047, rmiConnector.getCsf(), rmiConnector.getSsf());
            rmiManagerStub = (IRmiManager) UnicastRemoteObject.exportObject(rmiManager, 2047, rmiConnector.getCsf(), rmiConnector.getSsf());
            registry = LocateRegistry.getRegistry(2047);
            try {
                registry.unbind("rmi");
            } catch (NotBoundException |  RemoteException ignored) {

            }
            try {
                registry.bind("rmi", rmiManagerStub);
            } catch (AlreadyBoundException | RemoteException e) {
                e.printStackTrace();
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }


    }

    public void stop(BundleContext context) {
        System.out.println("Stopping the bundle- Rmi Connector");
    }

}