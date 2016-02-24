/**
 * Copyright (c) 1999-2007, Fiorano Software Technologies Pvt. Ltd. and affiliates.
 * Copyright (c) 2008-2015, Fiorano Software Pte. Ltd. and affiliates.
 * <p>
 * All rights reserved.
 * <p>
 * This software is the confidential and proprietary information
 * of Fiorano Software ("Confidential Information").  You
 * shall not disclose such Confidential Information and shall use
 * it only in accordance with the terms of the license agreement
 * enclosed with this product or entered into with Fiorano.
 */

package com.fiorano.openesb.utils.logging.api;

import com.fiorano.openesb.utils.exception.FioranoException;
import org.apache.log4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Hashtable;


public class FioranoClientLogger implements IFioranoLoggerFactory {


    private HashMap loggerMap = new HashMap();


    public synchronized Logger getLog4JLogger(String category)
            throws FioranoException {

        // dummy implementation

        return null;
    }


    /**
     * This API is invoked by FMQComponents to fetch IFioranoLogger runtime.
     *
     * @param category String
     * @return IFioranoLogger
     * @throws FioranoException
     */
    public synchronized IFioranoLogger getLogger(String category)
            throws FioranoException {
        String temp = category;
        int index = temp.lastIndexOf(".");

        if (index != -1) {
            temp = temp.substring(0, index);
            getLogger(temp);
        }

        return _getLogger(category);
    }


    /**
     * Returns all components for object
     */

    public Hashtable getAllComponents() {
        return new Hashtable();
    }


    /**
     * This API is invoked by FMQComponents to fetch IFioranoLogger runtime.
     *
     * @param category String
     * @return IFioranoLogger
     * @throws FioranoException if its not able to get the logger.
     */
    private IFioranoLogger _getLogger(String category)
            throws FioranoException {
        IFioranoLogger logger;
        logger = (IFioranoLogger) loggerMap.get(category);

        if (logger != null)
            return logger;

        else {
            logger = new FioranoSLF4JLogger(LoggerFactory.getLogger(category));
            loggerMap.put(category, logger);
        }
        return logger;
    }

    public int getLevelForComponent(String component) {
        return 0;
    }

    public void setTraceLevel(int level) {

    }

    public void setTraceLevel(String component, int level) {
    }

}
