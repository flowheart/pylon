package com.yotouch.core.workflow;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;

import com.yotouch.core.entity.Entity;
import com.yotouch.core.runtime.DbSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.yaml.snakeyaml.Yaml;

import com.yotouch.core.Consts;
import com.yotouch.core.config.Configure;

@Service
public class WorkflowManagerImpl implements WorkflowManager {
    
    private static final Logger logger = LoggerFactory.getLogger(WorkflowManagerImpl.class);
    
    @Autowired
    private Configure config;
    
    Map<String, Workflow> wfMap;
    
    @Autowired
    private DbSession dbSession;
    
    public void reload() {
        this.initWorkflow();
    }
    
    @PostConstruct
    void initWorkflow() {
        this.wfMap = new HashMap<>();
        
        this.loadFileWorkflow();
        this.loadDbWorkflow();
    }
    
    private void loadFileWorkflow() {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        try {
            Resource[] resources = resolver.getResources("classpath*:/etc/workflows.yaml");
            for (Resource resource : resources) {
                InputStream is = resource.getInputStream();
                parseWorkflowConfigInputStream(is);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        File ytHome = config.getRuntimeHome();
        if (ytHome != null) {
            for (File ah : ytHome.listFiles()) {
                if (ah.isDirectory()) {
                    if (ah.getName().startsWith("addon-")
                            || ah.getName().startsWith("app-")) {
                        parserWorkflowConfigFile(new File(ah, "etc/workflows.yaml"));
                    }
                }
            }
        }

        // NOTE: 这是为了测试用的
        String pylonTest = System.getProperty("PYLON_TEST");
        if (pylonTest != null && pylonTest.toLowerCase().equals("true")) {
            parserWorkflowConfigFile(new File(ytHome, "etc/workflows.yaml"));
        }

    }
    
    private void loadDbWorkflow() {
        List<Entity> wfList = dbSession.queryRawSql(
                "workflow",
                "status = ?",
                new Object[]{ Consts.STATUS_NORMAL }
        );
        
        for (Entity wf: wfList) {

            WorkflowImpl wfi = new WorkflowImpl(wf.getUuid(), wf.v("name"));
            wfi.setDisplayName(wf.v("displayName"));
            
            List<Entity> stateList = dbSession.queryRawSql(
                    "workflowState",
                    "workflowUuid = ?",
                    new Object[]{wf.getUuid()}
            );
            
            Map<String, String> stateNameMap = new HashMap<>();
            for (Entity state: stateList) {
                WorkflowStateImpl wfState = new WorkflowStateImpl(state.v("name"));
                wfState.setDisplayName(state.v("displayName"));
                
                String type = state.v("type");
                checkStateType(wfState, type);

                wfi.addState(wfState);
                wfState.setWorkflow(wfi);
                
                stateNameMap.put(state.getUuid(), wfState.getName());
                
            }
            
            List<Entity> actionList = dbSession.queryRawSql(
                    "workflowAction",
                    "workflowUuid = ?",
                    new Object[]{ wf.getUuid() }
            );
            
            for (Entity action: actionList) {
                WorkflowActionImpl wfAction = new WorkflowActionImpl(action.v("name"));
                wfAction.setDisplayName(action.v("displayName"));
                
                String from = action.v("fromState");
                if (Consts.WORKFLOW_STATE_ANY_STATE.equals(from)) {
                    wfAction.setFrom(WorkflowStateImpl.ANY_STATE);
                } else {
                    WorkflowStateImpl fromState = (WorkflowStateImpl) wfi.getState(stateNameMap.get(from));
                    wfAction.setFrom(fromState);
                    fromState.addOutAction(wfAction);   
                }
                
                String toName = action.v("toState");
                if (Consts.WORKFLOW_STATE_SELF_STATE.equals(toName)) {
                    wfAction.setTo(WorkflowStateImpl.SELF_STATE);
                    wfAction.setType(Consts.WORKFLOW_ACTION_TYPE_TO_SELF);
                } else {
                    WorkflowStateImpl toState = (WorkflowStateImpl) wfi.getState(stateNameMap.get(toName));
                    wfAction.setTo(toState);
                    toState.addInAction(wfAction);
                }

                wfi.addAction(wfAction);
                wfAction.setWorkflow(wfi);
            }

            this.wfMap.put(wf.v("name"), wfi);
        }
    }

    private void checkStateType(WorkflowStateImpl wfState, String type) {
        if (Consts.WORKFLOW_STATE_TYPE_START.equalsIgnoreCase(type)) {
            wfState.setType(Consts.WORKFLOW_STATE_TYPE_START);
        } else if (Consts.WORKFLOW_STATE_TYPE_FINISH.equalsIgnoreCase(type)) {
            wfState.setType(Consts.WORKFLOW_STATE_TYPE_FINISH);
        } else {
            wfState.setType(Consts.WORKFLOW_STATE_TYPE_NORMAL);
        }
    }
    
    private void parseWorkflowConfigInputStream(InputStream is) {
        Yaml yaml = new Yaml();
        @SuppressWarnings("unchecked")
        Map<String, Object> m = (Map<String, Object>) yaml.load(is);
        @SuppressWarnings("unchecked")
        List<Object> workflows = (List<Object>) m.get("workflows");
        logger.info("workflows " + workflows);

        if (workflows == null) {
            return ;
        }

        for (Object o: workflows) {
            @SuppressWarnings("unchecked")
            Map<String, Object> wfMap = (Map<String, Object>) o;
            this.buildWorkflow(wfMap);
        }

    }

    private void parserWorkflowConfigFile(File workflowFile) {
        logger.info("Parse workflow " + workflowFile);
        if (workflowFile.exists()) {
            
            try {
                InputStream is = new FileInputStream(workflowFile);
                parseWorkflowConfigInputStream(is);

            } catch (FileNotFoundException e) {
            }

        }
    }


    @SuppressWarnings("unchecked")
    private void buildWorkflow(Map<String, Object> wfMap) {
        String name = (String) wfMap.get("name");
        
        WorkflowImpl wfi = new WorkflowImpl("uuid-" + name, name);
        
        List<Map<String, String>> stateList = (List<Map<String, String>>) wfMap.get("states");
        for (Map<String, String> stMap: stateList) {
            String stateName = stMap.get("name");
            WorkflowStateImpl wfState = new WorkflowStateImpl(stateName);
            
            String stateDispName = stMap.get("displayName");
            if (StringUtils.isEmpty(stateDispName)) {
                stateDispName = stateName;
            }
            
            wfState.setDisplayName(stateDispName);
            
            String type = stMap.get("type");
            checkStateType(wfState, type);

            wfi.addState(wfState);
            wfState.setWorkflow(wfi);
        }
        
        List<Map<String, String>> actionList = (List<Map<String, String>>) wfMap.get("actions");
        for (Map<String, String> actMap: actionList) {
            String actionName = actMap.get("name");
            WorkflowActionImpl wfAction = new WorkflowActionImpl(actionName);
            
            String dispName = actMap.get("displayName");
            if (StringUtils.isEmpty(dispName)) {
                dispName = name;
            }
            wfAction.setDisplayName(dispName);
            
            
            String fromName = actMap.get("from");
            if (Consts.WORKFLOW_STATE_ANY_STATE.equalsIgnoreCase(fromName)) {
                wfAction.setFrom(WorkflowStateImpl.ANY_STATE);
            } else {
                WorkflowStateImpl fromState = (WorkflowStateImpl) wfi.getState(fromName);
                wfAction.setFrom(fromState);
                
                fromState.addOutAction(wfAction);
            }
            
            String toName = actMap.get("to");
            if (Consts.WORKFLOW_STATE_SELF_STATE.equalsIgnoreCase(toName)) {
                wfAction.setTo(WorkflowStateImpl.SELF_STATE);
                wfAction.setType(Consts.WORKFLOW_ACTION_TYPE_TO_SELF);
            } else {
                WorkflowStateImpl toState = (WorkflowStateImpl) wfi.getState(toName);
                wfAction.setTo(toState);
                toState.addInAction(wfAction);
            }
            
            wfi.addAction(wfAction);
            wfAction.setWorkflow(wfi);

        }
        
        
        this.wfMap.put(name, wfi);        
    }

    @Override
    public Workflow getWorkflow(String name) {
        return this.wfMap.get(name);
    }

}
