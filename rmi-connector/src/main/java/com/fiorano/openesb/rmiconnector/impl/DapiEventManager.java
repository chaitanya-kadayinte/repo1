package com.fiorano.openesb.rmiconnector.impl;

import com.fiorano.openesb.application.configuration.data.NamedObject;
import com.fiorano.openesb.application.configuration.data.ObjectCategory;
import com.fiorano.openesb.events.*;
import com.fiorano.openesb.namedconfig.ConfigurationRepositoryEventListener;
import com.fiorano.openesb.namedconfig.NamedConfigRepository;
import com.fiorano.openesb.rmiconnector.api.IApplicationManagerListener;
import com.fiorano.openesb.rmiconnector.api.IConfigurationRepositoryListener;
import com.fiorano.openesb.rmiconnector.api.IRepoEventListener;
import com.fiorano.openesb.utils.exception.FioranoException;
import com.fiorano.openesb.utils.queue.FioranoQueueImpl;
import com.fiorano.openesb.utils.queue.IFioranoQueue;

import java.rmi.NoSuchObjectException;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.concurrent.*;

/**
 * @author : amit,Suresh
 * @version : 2.0
 * @Date : Aug 12, 2008
 */
public class DapiEventManager implements EventListener {

    //Fes Event Manager
    private EventsManager fesEventManager;
    //Configuration Repository instance
    private NamedConfigRepository configurationRepository;
    //Application Specific Event listeners
    private final Hashtable<String, IApplicationManagerListener> appEventListeners = new Hashtable<String, IApplicationManagerListener>();
    private Hashtable<String, IRepoEventListener> microServiceRepoEventListeners = new Hashtable<String, IRepoEventListener>();
    private final Hashtable<String, IConfigurationRepositoryListener> configurationRepoEventListeners = new Hashtable<String, IConfigurationRepositoryListener>();
     private static final String DAPICONSTANT = "$";

    private static final String DELIMITER = "__";
    //Max Number of Events to Store in Queue. If the count is -1. It is INFINITE
    private int maxBufferedEventsCount = 256;
    //Queue to Store the System Events
    private final transient IFioranoQueue eventsQueue;
    //Queue to Store the Configuration Events
    private final transient IFioranoQueue configurationEventsQueue;
    //Queue to Store the Configuration Events
    //Condition to check before reading Events from Queue.
    private boolean isConnectionAlive;
    //Separate Thread to read events from Queue.
    private ReceiverThread receiverThread;
    //Configuration Repository Event Receiver Thread
    private ConfigurationEventReceiverThread configurationRepositoryEventReceiverthread;
    //Configuration Repository Event Receiver Thread
    //Service to execute event tasks in a thread pool
    private ExecutorService exec;
    private ConfigurationEventListener configurationEventListener;

    /**
     * stores the ipaddress(s) of all clients connected to the fes.
     * key-handleID vs object - ipaddress
     */
    private Hashtable<String, String> clientIPAddresses;


    /**
     * Constructor
     *
     * @param fesEventManager fes events manager
     */
//    public DapiEventManager(IFESEventsManager fesEventManager) {
//        this.fesEventManager = fesEventManager;
//    }

    /**
     * Constructor
     *
     * @param fesEventManager fesEvents Mangaer
     */
    public DapiEventManager(EventsManager fesEventManager, NamedConfigRepository namedConfigRepository) {
        this.fesEventManager = fesEventManager;
        this.eventsQueue = new FioranoQueueImpl();
        this.configurationEventsQueue = new FioranoQueueImpl();
        this.configurationRepository = namedConfigRepository;
    }

    public void setFesEventManager(EventsManager fesEventManager) {
        this.fesEventManager = fesEventManager;
    }

    public void setClientIPAddresses(Hashtable<String, String> clientIPAddresses) {
        this.clientIPAddresses = clientIPAddresses;
    }

    /**
     * On FES Event
     *
     * @param event FES event
     * @throws FioranoException
     */
    public void onEvent(Event event) throws FioranoException {
        pushEventToQueue(event);
    }

    private void pushEventToQueue(Event event) {
        if (event == null) {
            return;
        }
        synchronized (eventsQueue) {
            if (maxBufferedEventsCount < 0 || eventsQueue.getSize() < maxBufferedEventsCount) {
                eventsQueue.pushWithNotify(event);
                return;
            }
        }
       System.out.println("cannot deliover event");
    }


    private void pushToConfigurationEventQueue(ConfigurationEvent event) {
        if (event == null) {
            return;
        }

        synchronized (configurationEventsQueue) {
            if (maxBufferedEventsCount < 0 || configurationEventsQueue.getSize() < maxBufferedEventsCount) {
                configurationEventsQueue.pushWithNotify(event);
                return;
            }
        }

        ObjectCategory objectCategory = event.getNamedObject().getObjectCategory();
        String category = objectCategory != null ? objectCategory.getConfigurationTypeAsString() : null;
    }


    public Event readEvent(long timeout) throws FioranoException {
        Object obj = eventsQueue.popWithWait(timeout);

        // long indicated that the connection handle is closed.
        if (obj instanceof Long)
            return null;
        else
            return (Event) obj;
    }
    public ConfigurationEvent readConfigurationEvent(long timeout) throws FioranoException {
        Object obj = configurationEventsQueue.popWithWait(timeout);

        // long indicated that the connection handle is closed.
        if (obj instanceof Long)
            return null;
        else
            return (ConfigurationEvent) obj;
    }

    private void startReceiving() {
        receiverThread = new ReceiverThread();
        receiverThread.setName("DAPI_EVENT_MANAGER_THREAD_FOR_RECEIVING_SYSTEM_EVENTS");
        receiverThread.start();

        configurationRepositoryEventReceiverthread = new ConfigurationEventReceiverThread();
        configurationRepositoryEventReceiverthread.setName("DAPI_EVENT_MANAGER_THREAD_FOR_RECEIVING_CONFIGURATION_EVENTS");
        configurationRepositoryEventReceiverthread.start();
    }

    private void stopRunning() {
        isConnectionAlive = false;
        receiverThread = null;
        configurationRepositoryEventReceiverthread = null;
    }

    public void registerMicroServiceRepoEventListener(IRepoEventListener listener, String handleId) {
        String key = handleId;
        if (microServiceRepoEventListeners.containsKey(key))
            microServiceRepoEventListeners.remove(key);
        microServiceRepoEventListeners.put(key, listener);
    }

    public void unRegisterMicroServiceRepoEventListener(String handleId) {
        if (microServiceRepoEventListeners.containsKey(handleId))
            microServiceRepoEventListeners.remove(handleId);
    }

    class ReceiverThread extends Thread {
        public void run() {
            while (isConnectionAlive) {
                try {
                    Event event = readEvent(0);
                    if (event != null)
                        processEvent(event);
                }
                catch (Exception e) {
                    e.printStackTrace();
                    isConnectionAlive = false;
                }
            }
        }
    }

    private void processEvent(Event event) {
         if (event instanceof ApplicationEvent) {
             final ApplicationEvent appEvent = (ApplicationEvent) event;
             final String appGUID = appEvent.getApplicationGUID();
             final float appVersion = Float.parseFloat(appEvent.getApplicationVersion());
             synchronized (appEventListeners) {
                 Enumeration keys = appEventListeners.keys();
                 while (keys.hasMoreElements()) {
                     String key = (String) keys.nextElement();
                     String substr = key.substring(key.indexOf(DAPICONSTANT) + 1);
                     if (substr.equalsIgnoreCase(appGUID + DELIMITER + appVersion)) {
                         final String handleId = key.substring(0, key.indexOf(DAPICONSTANT));
                         final IApplicationManagerListener appEventListener = appEventListeners.get(key);
                         exec.execute(new Runnable() {
                             public void run() {
                                 try {
                                     if (appEvent.getApplicationEventType() == ApplicationEvent.ApplicationEventType.APPLICATION_LAUNCHED)
                                         appEventListener.applicationStarted(appVersion);
                                     else if (appEvent.getApplicationEventType() == ApplicationEvent.ApplicationEventType.APPLICATION_LAUNCH_STARTED)
                                         appEventListener.applicationStarting(appVersion);
                                     else if (appEvent.getApplicationEventType() == ApplicationEvent.ApplicationEventType.APPLICATION_STOPPED)
                                         appEventListener.applicationStopped(appVersion);
                                 } catch (NoSuchObjectException e) {
                                     //ignore
                                     // Dont want to log unnecessarily
                                 } catch (Throwable t) {
                                     t.printStackTrace();
                                 }
                             }
                         });
                     }
                 }
             }
         } else if (event instanceof MicroServiceEvent) {
             final MicroServiceEvent serviceEvent = (MicroServiceEvent) event;
             final String appGUID = serviceEvent.getApplicationGUID();
             final String appVersion = serviceEvent.getApplicationVersion();
             final String serviceInstanceName = serviceEvent.getServiceInstance();
             final float serviceVersion = serviceEvent.getServiceVersion();
             synchronized (appEventListeners) {
                 Enumeration keys = appEventListeners.keys();
                 while (keys.hasMoreElements()) {
                     String key = (String) keys.nextElement();
                     String substr = key.substring(key.indexOf(DAPICONSTANT) + 1);
                     if (substr.equalsIgnoreCase(appGUID +DELIMITER+ appVersion)) {
                         final String handleId = key.substring(0, key.indexOf(DAPICONSTANT));
                         final IApplicationManagerListener appEventListener = appEventListeners.get(key);
                         exec.execute(new Runnable() {
                             public void run() {
                                 try {
                                     if (serviceEvent.getMicroServiceEventType() == MicroServiceEvent.MicroServiceEventType.SERVICE_LAUNCHED)
                                         appEventListener.serviceInstanceStarted(serviceInstanceName, serviceVersion);
                                     else if (serviceEvent.getMicroServiceEventType() == MicroServiceEvent.MicroServiceEventType.SERVICE_LAUNCHING)
                                         appEventListener.serviceInstanceStarting(serviceInstanceName, serviceVersion);
                                     else if (serviceEvent.getMicroServiceEventType() == MicroServiceEvent.MicroServiceEventType.SERVICE_STOPPED)
                                         appEventListener.serviceInstanceStopped(serviceInstanceName, serviceVersion);
                                 }
                                 catch (NoSuchObjectException e) {
                                     //ignore
                                 }
                                 catch (Throwable t) {
                                     // It is necessary to ignore this exception and continue with sending this event to other event listeners
                                     // This is because in case one of the connected clients gets disconnected because of some reason,
                                     // then this exception will be thrown for that client. But this exception should not stop the server
                                     // from sending this event to other connected clients
                                     t.printStackTrace();
                                     //logger.error(Bundle.class, Bundle.ERROR_SEND_SERVICE_EVENT, clientIPAddresses.get(handleId), serviceInstanceName, serviceEvent.getEventDescription(), t);
                                 }
                             }
                         });
                     }
                 }
             }

         } else if (event instanceof MicroServiceRepoUpdateEvent) {
             final MicroServiceRepoUpdateEvent serviceRepoEvent = (MicroServiceRepoUpdateEvent) event;
             final String serviceGUID = serviceRepoEvent.getServiceGUID();
             final float serviceVersion = Float.parseFloat(serviceRepoEvent.getServiceVersion());
             synchronized (microServiceRepoEventListeners) {
                 Enumeration keys = microServiceRepoEventListeners.keys();
                 while (keys.hasMoreElements()) {
                     final String handleId = (String) keys.nextElement();
                     final IRepoEventListener serviceEventListener = microServiceRepoEventListeners.get(handleId);
                     exec.execute(new Runnable() {
                         public void run() {
                             try {
                                 if (serviceRepoEvent.getServiceStatus().equals(MicroServiceRepoUpdateEvent.UNREGISTERED_SERVICE_EDITED) ||
                                         serviceRepoEvent.getServiceStatus().equals(MicroServiceRepoUpdateEvent.REGISTERED_SERVICE_EDITED) ||
                                         serviceRepoEvent.getServiceStatus().equals(MicroServiceRepoUpdateEvent.SERVICE_CREATED))
                                     serviceEventListener.descriptorModified(serviceGUID, serviceVersion);
                                 else if (serviceRepoEvent.getServiceStatus().equals(MicroServiceRepoUpdateEvent.SERVICE_REGISTERED))
                                     serviceEventListener.serviceDeployed(serviceGUID, serviceVersion);
                                 else if (serviceRepoEvent.getServiceStatus().equals(MicroServiceRepoUpdateEvent.SERVICE_REMOVED))
                                     serviceEventListener.serviceDeleted(serviceGUID, serviceVersion);
                                 else if (serviceRepoEvent.getServiceStatus().equals(MicroServiceRepoUpdateEvent.RESOURCE_REMOVED))
                                     serviceEventListener.resourceDeleted(serviceRepoEvent.getResourceName(), serviceGUID, serviceVersion);
                                 else if (serviceRepoEvent.getServiceStatus().equals(MicroServiceRepoUpdateEvent.RESOURCE_UPLOADED) ||
                                         serviceRepoEvent.getServiceStatus().equals(MicroServiceRepoUpdateEvent.RESOURCE_CREATED))
                                     serviceEventListener.resourceDeployed(serviceRepoEvent.getResourceName(), serviceGUID, serviceVersion);
                             }
                             catch (NoSuchObjectException e) {
                                 //ignore
                             }
                             catch (Throwable t) {
                                 // It is necessary to ignore this exception and continue with sending this event to other event listeners
                                 // This is because in case one of the connected clients gets disconnected because of some reason,
                                 // then this exception will be thrown for that client. But this exception should not stop the server
                                 // from sending this event to other connected clients
                                 t.printStackTrace();
                                // logger.error(Bundle.class, Bundle.ERROR_SEND_SERVICE_REPOSITORY_EVENT, clientIPAddresses.get(handleId), serviceGUID, serviceRepoEvent.getEventDescription(), t);
                             }
                         }
                     });
                 }
             }
         }
    }

    class ConfigurationEventReceiverThread extends Thread {
        public void run() {
            while (isConnectionAlive) {
                try {
                    ConfigurationEvent configurationEvent = readConfigurationEvent(0);
                    if (configurationEvent != null)
                        processConfigurationEvent(configurationEvent.getNamedObject(), configurationEvent.getEventType());
                }
                catch (Exception e) {
                    e.printStackTrace();
                    isConnectionAlive = false;
                }
            }
        }

    }

    private class ConfigurationEventListener implements ConfigurationRepositoryEventListener {
        public void configurationPersisted(NamedObject namedObject) {
            ConfigurationEvent event = new ConfigurationEvent(1, namedObject);
            pushToConfigurationEventQueue(event);
        }

        public void configurationDeleted(NamedObject namedObject) {
            ConfigurationEvent event = new ConfigurationEvent(2, namedObject);
            pushToConfigurationEventQueue(event);
        }
    }

    private class ConfigurationEvent {
        private int eventType;
        private NamedObject namedObject;

        private ConfigurationEvent(int eventType, NamedObject namedObject) {
            this.eventType = eventType;
            this.namedObject = namedObject;
        }

        public int getEventType() {
            return eventType;
        }

        public NamedObject getNamedObject() {
            return namedObject;
        }
    }

    private void processConfigurationEvent(NamedObject event, int eventType) {
        if (eventType == 1) {
            synchronized (configurationRepoEventListeners) {
                Enumeration<String> keys = configurationRepoEventListeners.keys();
                while (keys.hasMoreElements()) {
                    String key = keys.nextElement();
                    IConfigurationRepositoryListener listener = configurationRepoEventListeners.get(key);
                    try {
                        listener.configurationPersisted(event);
                    } catch (NoSuchObjectException e) {
                        //Ignore. Dont want to log unnecessarily
                    } catch (Throwable e) {
                        String category = event.getObjectCategory() != null ? event.getObjectCategory().getConfigurationTypeAsString() : null;
                        e.printStackTrace();
                    }
                }
            }
        } else if(eventType == 2) {
            synchronized (configurationRepoEventListeners) {
                Enumeration<String> keys = configurationRepoEventListeners.keys();
                while (keys.hasMoreElements()) {
                    String key = keys.nextElement();
                    IConfigurationRepositoryListener listener = configurationRepoEventListeners.get(key);
                    try {
                        listener.configurationDeleted(event);
                    } catch (NoSuchObjectException e) {
                        //Ignore. Dont want to log unnecessarily
                    } catch (Throwable e) {
                        String category = event.getObjectCategory() != null ? event.getObjectCategory().getConfigurationTypeAsString() : null;
                        e.printStackTrace();
                    }
                }
            }
        }
    }


    public void registerConfigurationRepositoryEventListener(IConfigurationRepositoryListener configurationRepositoryListener, String handleId) {
        if (configurationRepoEventListeners.containsKey(handleId))//have one listener for one eStudio client always.
            configurationRepoEventListeners.remove(handleId);
        configurationRepoEventListeners.put(handleId, configurationRepositoryListener);
    }

    public void registerApplicationEventListener(IApplicationManagerListener appEventListener, String appGUID, float appVersion, String handleId) {
        String key = handleId + DAPICONSTANT + appGUID + DELIMITER +appVersion;
        if (appEventListeners.containsKey(key))
            appEventListeners.remove(key);
        appEventListeners.put(key, appEventListener);
    }

    public void unRegisterApplicationEventListener(IApplicationManagerListener appEventListener, String appGUID, float appVersion, String handleId) {
        String key = handleId + DAPICONSTANT + appGUID +DELIMITER+ appVersion;
        if (appEventListeners.containsKey(key))
            appEventListeners.remove(key);
    }

    public void unRegisterConfigurationRepositoryEventListener(String handleId) {
        if (configurationRepoEventListeners.containsKey(handleId))
            configurationRepoEventListeners.remove(handleId);
    }

    public void startEventListener() {
        // In customer development scenario, a lot of changes would be made with many events received by eStudio amid frequent pauses.
        // In the default cached thread pool, any pause greater than 60 secs would terminate all threads in the pool leading to
        // significant repeated thread creation overhead. Resolving by setting the keepAliveTime to 30 mins.
        // To prevent initial application launch to appear delayed, starting with 5 threads in the core pool rather than 0 threads.

//        exec = Executors.newCachedThreadPool(new DaemonThreadFactory());
        exec = new ThreadPoolExecutor(5, Integer.MAX_VALUE,
                30L, TimeUnit.MINUTES,
                new SynchronousQueue<Runnable>(),
                new DaemonThreadFactory());
        if (fesEventManager != null)
            fesEventManager.registerEventListener(this);

        if (configurationRepository != null) {
            configurationEventListener = new ConfigurationEventListener();
            configurationRepository.registerEventListener(configurationEventListener);
        }
        isConnectionAlive = true;
        startReceiving();
    }


    public void stopEventListener() {
        if (fesEventManager != null)
            fesEventManager.unRegisterEventListener(this);
        if (configurationRepository != null)
            configurationRepository.unRegisterEventListener(configurationEventListener);
        stopRunning();
        removeAllListeners();
        exec.shutdown();
    }

    public void unregisterAllApplicationListeners(String handleID) {
        synchronized (appEventListeners) {
            Enumeration keys = appEventListeners.keys();
            while (keys.hasMoreElements()) {
                String key = (String) keys.nextElement();
                String substr = key.substring(0, key.indexOf(DAPICONSTANT));
                if (substr.equalsIgnoreCase(handleID)) {
                    appEventListeners.remove(key);
                }
            }
        }
    }

    private void removeAllListeners() {
        appEventListeners.clear();
    }

    public void unRegisterOldListeners(String handleID) {
        unregisterAllApplicationListeners(handleID);
    }

    private class DaemonThreadFactory implements ThreadFactory {
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r);
            thread.setDaemon(true);
            return thread;
        }
    }
}
