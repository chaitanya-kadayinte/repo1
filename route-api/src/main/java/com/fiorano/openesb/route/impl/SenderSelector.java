package com.fiorano.openesb.route.impl;

import com.fiorano.openesb.route.FilterMessageException;
import com.fiorano.openesb.route.RouteOperationHandler;
import com.fiorano.openesb.transport.impl.jms.JMSMessage;
import com.fiorano.openesb.utils.JmsMessageUtil;
import com.fiorano.openesb.utils.SourceContext;
import com.fiorano.openesb.utils.exception.FioranoException;

import javax.jms.JMSException;
import javax.jms.Message;
import java.util.Enumeration;

/**
 * Created by root on 3/3/16.
 */
public class SenderSelector implements RouteOperationHandler<JMSMessage> {
    private String sourceName;
    private String appName_version;

    public SenderSelector(SenderSelectorConfiguration configuration) {
        this.sourceName = configuration.getSourceName();
        this.appName_version = configuration.getAppName_version();
    }

    @Override
    public void handleOperation(JMSMessage message) throws FilterMessageException, FioranoException {
        String context = sourceName + "|" + appName_version;
        if (isMessageSelected(message)) {
            throw new FilterMessageException();
        }
    }

    public boolean isMessageSelected(JMSMessage message) {
        if (sourceName == null)
            return false;

        try {
            CarryForwardContext carryForwardContext = (CarryForwardContext) JmsMessageUtil.getCarryForwardContext(message.getMessage());

            //System.out.println("RouteSourceSelector:: carryForwardContext = " + carryForwardContext);

            if (carryForwardContext == null)
                return false;

            Enumeration sourceContexts = carryForwardContext.getContexts();
            String[] appDetails = appName_version.split(":");            // splitting on ':' to get appDetails[0](appName) and appDetails[1](version)
            if (appDetails.length > 1) {
                if (appDetails[0] == null)                                 // if current application name is absent then send back a false as route selector cant decide whether to send the message forward or not
                    return false;
                if (appDetails[1] == null)                                 // if current application version is absent then send back a false as route selector cant decide whether to send the message forward or not
                    return false;
            }


            while (sourceContexts.hasMoreElements()) {
                SourceContext sourceContext = (SourceContext) sourceContexts.nextElement();

                if (sourceContext.getAppInstName() == null)                                // source context has missing application information, move to next source context
                    continue;
                if (sourceContext.getAppInstVersion() == null)                             // source context has missing version information, move to next source context
                    continue;
                if (sourceContext.getSrvInstName() == null)                                // source context has missing service information, move to next source context
                    continue;

                String[] multipleSources = sourceName.split(",");
                if (multipleSources != null) {
                    for (String multipleSource : multipleSources) {
                        multipleSource = multipleSource.trim();
                        if (sourceContext.getSrvInstName().equalsIgnoreCase(multipleSource)) {
                            if (sourceContext.getAppInstName().equalsIgnoreCase(appDetails[0])) {
                                if (sourceContext.getAppInstVersion().equalsIgnoreCase(appDetails[1])) {
                                    return true;
                                }
                            }

                        }
                        String[] app_version_serv = multipleSource.split("\\.");
                        if (app_version_serv.length > 1) {
                            String[] app_version = app_version_serv[0].split(":");
                            if (app_version.length > 1) {
                                if (app_version[0] == null)
                                    continue;                                // missing application name information in details set by user, move to next comma separated source value
                                if (app_version[1] == null)
                                    continue;                                // missing version number information in details set by user, move to next comma separated source value
                                if (app_version_serv[1] == null)
                                    continue;                                // missing service instance name information in details set by user, move to next comma separated source value
                                app_version[1] = app_version[1].replace("_", ".");
                                if ((sourceContext.getAppInstName().equalsIgnoreCase(app_version[0])) && (sourceContext.getAppInstVersion().equalsIgnoreCase(app_version[1])) && (sourceContext.getSrvInstName().equalsIgnoreCase(app_version_serv[1])))
                                    return true;                  // all categories match returning true value to send message across.
                            }
                        }
                    }
                }

            }
            return false;
        } catch (JMSException exp) {
            return false;
        }
    }
}