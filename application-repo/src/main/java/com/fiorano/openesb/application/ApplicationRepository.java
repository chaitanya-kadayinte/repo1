package com.fiorano.openesb.application;

import com.fiorano.openesb.application.application.*;
import com.fiorano.openesb.utils.FileUtil;
import com.fiorano.openesb.utils.exception.FioranoException;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;

/**
 * Created by Janardhan on 1/6/2016.
 */
public class ApplicationRepository {

    String applicationRepoPath = System.getProperty("karaf.base") + File.separator + "repository" + File.separator + "applications";

    public ApplicationRepository(){

    }

    public Application readApplication(String appGuid, String version){
        try {
           return ApplicationParser.readApplication(new File(getApplicationRepoPath()+ File.separator+appGuid+File.separator+version), false);
        } catch (FioranoException e) {
            e.printStackTrace();
        }
        return null;
    }

    public List<String> listApplications() {

        return null;
    }

    public String getApplicationRepoPath(){
        return applicationRepoPath;
    }

    public boolean applicationExists(String appGuid, String version) {
        return false;
    }

    public void saveApplication(Application application, File tempAppFolder, String userName, byte[] zippedContents, String handleID) {
        String appGUID = application.getGUID().toUpperCase();
        float versionNumber = application.getVersion();
        File tempAppFolderObject=null;
        try{
            float version = application.getVersion();
            File applicationFolder = new File(getAppRootDirectory(appGUID,version));

            if(!applicationFolder.exists() && !applicationFolder.isDirectory())
                applicationFolder.mkdirs();
            else{
                //Remove redundant config files
                File configFolder = new File(getAppRootDirectory(appGUID, version)+File.separator+"config");
                if(configFolder.exists()){
                    List<ServiceInstance> serviceInstances = application.getServiceInstances();
                    for (ServiceInstance instance : serviceInstances) {
                        String configuration = instance.getConfiguration();
                        String fileName = instance.getName() + ".xml";
                        File configFile = new File(getConfigurationFileName(appGUID, version, fileName));
                        if (configuration == null) {
                            if (configFile.exists())
                                configFile.delete();
                        }

                        //Remove redundant output port transformation files
                        List<OutputPortInstance> outputPortInstances = instance.getOutputPortInstances();
                        for(OutputPortInstance port : outputPortInstances) {
                            Transformation transformation = port.getApplicationContextTransformation();

                            String script = transformation != null ? transformation.getScript() : null;
                            String scriptFileName = "script" + ".xml";
                            File scriptFile = new File(getPortTransformationFileName(appGUID, instance.getName(), port.getName(), version, scriptFileName));
                            if(script == null && scriptFile.exists())
                                scriptFile.delete();

                            String project = transformation != null ? transformation.getProject() : null;
                            String projectFileName = "project" + ".fmp";
                            File projectFile = new File(getPortTransformationFileName(appGUID, instance.getName(), port.getName(), version, projectFileName));
                            if(project == null){
                                if(projectFile.exists())
                                    projectFile.delete();

                                File projectDir = new File(getPortTransformationDirName(appGUID, instance.getName(), port.getName(), version));
                                if(projectDir.exists())
                                    projectDir.delete();
                            }else if(!new File(getPortTransformationDir(tempAppFolder.getAbsolutePath(), instance.getName(), port.getName()) + "project" + ".fmp").exists()){
                                //If new transformation project is not in single file format, delete the already exisiting single file.
                                if(projectFile.exists())
                                    projectFile.delete();
                            }

                            File portTransformationDir = new File(getPortTransformationDirName(appGUID, instance.getName(), port.getName(), version));
                            if(portTransformationDir.exists() && portTransformationDir.list().length==0)
                                portTransformationDir.delete();
                        }
                    }

                    for(Route route : application.getRoutes()) {
                        MessageTransformation messageTransformation = route.getMessageTransformation();

                        String script = messageTransformation != null ? messageTransformation.getScript() : null;
                        String scriptFileName = "script" + ".xml";
                        File scriptFile = new File(getRouteTransformationFileName(appGUID, route.getName(), version, scriptFileName));
                        if(script == null && scriptFile.exists())
                            scriptFile.delete();

                        String jmsScript = messageTransformation != null ? messageTransformation.getJMSScript() : null;
                        String jmsScriptFileName = "jmsScript" + ".xml";
                        File jmsScriptFile = new File(getRouteTransformationFileName(appGUID, route.getName(), version, jmsScriptFileName));
                        if(jmsScript == null && jmsScriptFile.exists())
                            jmsScriptFile.delete();

                        String project = messageTransformation != null ? messageTransformation.getProject() : null;
                        String projectFileName = "project" + ".fmp";
                        File projectFile = new File(getRouteTransformationFileName(appGUID, route.getName(), version, projectFileName));
                        if(project == null){
                            if(projectFile.exists())
                                projectFile.delete();

                            File projectDir = new File(getRouteTransformationDirName(appGUID, route.getName(), version));
                            if(projectDir.exists())
                                projectDir.delete();
                        }else if(!new File(getRouteTransformationDir(tempAppFolder.getAbsolutePath(), route.getName()) + "project" + ".fmp").exists()){
                            //If new transformation project is not in single file format, delete the already exisiting single file.
                            if(projectFile.exists())
                                projectFile.delete();
                        }

                        File routeTransformationDir = new File(getRouteTransformationDirName(appGUID, route.getName(), version));
                        if(routeTransformationDir.exists() && routeTransformationDir.list().length == 0)
                            routeTransformationDir.delete();
                    }
                }

                //Remove redundant schema files
                File schemaFolder = new File(getAppRootDirectory(appGUID, version)+File.separator + "schemas");
                if(schemaFolder.exists()){
                    File[] children = schemaFolder.listFiles();
                    for (int i=0; i<children.length; i++) {
                        children[i].delete();
                    }
                }
            }

            File eventProcessXMLFile = null;
            try{

                //As we are blindly copying the Event Process zip sent by tool (eStudio), we should parse the EventProcess.xml and write it again
                //This is being done to write schema-version element correctly into the EventProcess.xml
                Application application2 = ApplicationParser.readApplication(tempAppFolder, null, false);

                ApplicationParser.checkAndSetNamedConfigurationIdentifiers(application2);
                application.setSchemaVersion(application2.getSchemaVersion());
                boolean namedConfigurationUsed = application2.getSchemaVersion().equals(ApplicationParser.NAMED_CONFIGURATION_SCHEMA_VERSION);
                application2.setUserName(userName);

                eventProcessXMLFile = new File(tempAppFolder.getAbsolutePath() + File.separator + ApplicationParser.EVENT_PROCESS_XML);
                application2.toXMLString(new FileOutputStream(eventProcessXMLFile), false);
                FileUtil.copyDirectory(applicationFolder, tempAppFolder);
            } finally{

            }
        } catch (FioranoException e) {

        } catch(Exception e){
            e.printStackTrace();

        } finally {
            try {
                FileUtil.deleteDir(tempAppFolder);
            } catch (Exception e) {
               e.printStackTrace();
            }
        }
    }

    private String getConfigurationFileName(String appGUID, float version, String fileName) {
        return applicationRepoPath+File.separator+appGUID.toUpperCase()+File.separator+version + File.separator + "config" + File.separator +fileName ;
    }

    private String getAppRootDirectory(String appGUID, float version) {
        return applicationRepoPath+File.separator+appGUID.toUpperCase()+File.separator+version;
    }


    private String getRouteTransformationFileName(String appGUID, String subDir, float version, String fileName){
        return getRouteTransformationDirName(appGUID, subDir, version) + fileName;
    }

    private String getRouteTransformationDirName(String appGUID, String subDir, float version){
        return applicationRepoPath+File.separator+appGUID.toUpperCase()+File.separator+version + File.separator + "transformations" + File.separator + "routes" + File.separator + (subDir != null ? subDir + File.separator : "");
    }

    private String getRouteTransformationDir(String applicationFolderPath, String subDir){
        return applicationFolderPath + File.separator + "transformations" + File.separator + "routes" + File.separator + (subDir != null ? subDir + File.separator : "");
    }

    private String getPortTransformationFileName(String appGUID, String serviceInstanceName, String portName, float version, String fileName){
        return getPortTransformationDirName(appGUID, serviceInstanceName, portName, version) + fileName;
    }

    private String getPortTransformationDirName(String appGUID, String serviceInstanceName, String portName, float version){
        return applicationRepoPath+File.separator+appGUID.toUpperCase()+File.separator+version + File.separator + "transformations" + File.separator + "ports" + File.separator + serviceInstanceName + File.separator + (portName != null ? portName + File.separator : "");
    }

    private String getPortTransformationDir(String applicationFolderPath, String serviceInstanceName, String portName){
        return applicationFolderPath + File.separator + "transformations" + File.separator + "ports" + File.separator + serviceInstanceName + File.separator + (portName != null ? portName + File.separator : "");
    }

    public void deleteApplication(String appGUID, String version) throws FioranoException {
        File f  = new File(getAppRootDirectory(appGUID, Float.valueOf(version)));
        if(f.exists()){
            FileUtil.deleteDir(f);
            if(f.getParentFile().isDirectory() || f.getParentFile().list().length==0){
                f.getParentFile().delete();
            }
        }else {
            throw new FioranoException("Application: " + appGUID+""+version+" does not exist in the repository");
        }

    }
}
