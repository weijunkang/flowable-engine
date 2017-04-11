/* Licensed under the Apache License, Version 2.0 (the "License");
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
package org.flowable.engine.test.bpmn.dynamic;

import java.util.Arrays;
import java.util.List;

import org.flowable.engine.impl.dynamic.DynamicEmbeddedSubProcessBuilder;
import org.flowable.engine.impl.dynamic.DynamicUserTaskBuilder;
import org.flowable.engine.impl.test.PluggableFlowableTestCase;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.task.Task;

public class DynamicBpmnInjectionTest extends PluggableFlowableTestCase {

    public void testInjectuserTaskInProcessInstance() {
        deployOneTaskTestProcess();

        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("oneTaskProcess");
        DynamicUserTaskBuilder taskBuilder = new DynamicUserTaskBuilder();
        taskBuilder.id("custom_task")
            .name("My injected task")
            .assignee("kermit");
        dynamicBpmnService.injectUserTaskInProcessInstance(processInstance.getId(), taskBuilder);

        List<Task> tasks = taskService.createTaskQuery().processInstanceId(processInstance.getId()).list();
        assertEquals(2, tasks.size());
        for (Task t : tasks) {
            taskService.complete(t.getId());
        }

        assertProcessEnded(processInstance.getId());
        removeDerivedDeployments();
    }

    public void testInjectParallelTask() {
        deployOneTaskTestProcess();

        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("oneTaskProcess");
        Task task = taskService.createTaskQuery().singleResult();

        DynamicUserTaskBuilder taskBuilder = new DynamicUserTaskBuilder();
        taskBuilder.id("custom_task")
            .name("My injected task")
            .assignee("kermit");
        dynamicBpmnService.injectParallelUserTask(task.getId(), taskBuilder);

        List<Task> tasks = taskService.createTaskQuery().processInstanceId(processInstance.getId()).list();
        assertEquals(2, tasks.size());
        for (Task t : tasks) {
            taskService.complete(t.getId());
        }
        assertProcessEnded(processInstance.getId());

        removeDerivedDeployments();
    }

    public void testInjectParallelTaskNoJoin() {
        deployOneTaskTestProcess();

        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("oneTaskProcess");
        Task task = taskService.createTaskQuery().singleResult();

        DynamicUserTaskBuilder taskBuilder = new DynamicUserTaskBuilder();
        taskBuilder.id("custom_task")
            .name("My injected task")
            .assignee("kermit")
            .setJoinParallelActivitiesOnComplete(false);
        dynamicBpmnService.injectParallelUserTask(task.getId(), taskBuilder);

        List<Task> tasks = taskService.createTaskQuery().processInstanceId(processInstance.getId()).orderByTaskName().asc().list();
        assertEquals(2, tasks.size());
        assertEquals(taskBuilder.getName(), tasks.get(0).getName());
        taskService.complete(tasks.get(0).getId());

        task = taskService.createTaskQuery().singleResult();
        assertEquals("The Task", task.getName());
        taskService.complete(task.getId());

        assertProcessEnded(processInstance.getId());
        removeDerivedDeployments();
    }

    public void testInjectParallelSubProcessSimple() {
        deployOneTaskTestProcess();
        Deployment deployment = repositoryService.createDeployment()
                .addClasspathResource("org/flowable/engine/test/bpmn/dynamic/dynamic_onetask.bpmn20.xml")
                .deploy();

        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("oneTaskProcess");
        Task task = taskService.createTaskQuery().singleResult();

        ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery()
                .processDefinitionKey("oneTaskV2")
                .singleResult();
        
        DynamicEmbeddedSubProcessBuilder subProcessBuilder = new DynamicEmbeddedSubProcessBuilder()
                .id("customSubprocess")
                .processDefinitionId(processDefinition.getId());
        dynamicBpmnService.injectParallelEmbeddedSubProcess(task.getId(), subProcessBuilder);

        List<Task> tasks = taskService.createTaskQuery().processInstanceId(processInstance.getId()).list();
        assertEquals(2, tasks.size());
        for (Task t : tasks) {
            taskService.complete(t.getId());
        }
        assertProcessEnded(processInstance.getId());

        repositoryService.deleteDeployment(deployment.getId(), true);

        removeDerivedDeployments();
    }

    public void testInjectParallelSubProcessComplex() {
        Deployment deployment = repositoryService.createDeployment()
                .addClasspathResource("org/flowable/engine/test/bpmn/dynamic/dynamic_test_process01.bpmn")
                .addClasspathResource("org/flowable/engine/test/bpmn/dynamic/dynamic_test_process02.bpmn")
                .deploy();

        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("testProcess01");
        List<Task> tasks = taskService.createTaskQuery().processInstanceId(processInstance.getId()).orderByTaskName().asc().list();
        assertEquals(4, tasks.size());
        assertEquals("Task A", tasks.get(0).getName());
        assertEquals("Task B", tasks.get(1).getName());
        assertEquals("Task C", tasks.get(2).getName());
        assertEquals("Task D", tasks.get(3).getName());

        Task taskB = tasks.get(1);
        ProcessDefinition subProcessDefinition = repositoryService.createProcessDefinitionQuery().processDefinitionKey("testProcess02").singleResult();
        dynamicBpmnService.injectParallelEmbeddedSubProcess(taskB.getId(), new DynamicEmbeddedSubProcessBuilder()
                .id("injectedSubProcess")
                .processDefinitionId(subProcessDefinition.getId()));

        tasks = taskService.createTaskQuery().processInstanceId(processInstance.getId()).orderByTaskName().asc().list();
        assertEquals(9, tasks.size());
        List<String> expectedTaskNames = Arrays.asList("Task A", "Task B", "Task C", "Task D", "five", "four", "one", "three", "two");
        for (int i=0; i<expectedTaskNames.size(); i++) {
            assertEquals(expectedTaskNames.get(i), tasks.get(i).getName());
        }

        for (Task task : tasks) {
            taskService.complete(task.getId());
        }

        Task afterBTask = taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
        assertEquals("After B", afterBTask.getName());
        taskService.complete(afterBTask.getId());

        Task afterSubProcessTask = taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
        assertEquals("After sub process", afterSubProcessTask.getName());
        taskService.complete(afterSubProcessTask.getId());
        assertProcessEnded(processInstance.getId());

        removeDerivedDeployments();
        repositoryService.deleteDeployment(deployment.getId(), true);
    }

    public void testInjectParallelSubProcessComplexNoJoin() {
        Deployment deployment = repositoryService.createDeployment()
                .addClasspathResource("org/flowable/engine/test/bpmn/dynamic/dynamic_test_process01.bpmn")
                .addClasspathResource("org/flowable/engine/test/bpmn/dynamic/dynamic_test_process02.bpmn")
                .deploy();

        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("testProcess01");
        List<Task> tasks = taskService.createTaskQuery().processInstanceId(processInstance.getId()).orderByTaskName().asc().list();
        assertEquals(4, tasks.size());

        Task taskB = tasks.get(1);
        ProcessDefinition subProcessDefinition = repositoryService.createProcessDefinitionQuery().processDefinitionKey("testProcess02").singleResult();
        dynamicBpmnService.injectParallelEmbeddedSubProcess(taskB.getId(), new DynamicEmbeddedSubProcessBuilder()
                .id("injectedSubProcess")
                .processDefinitionId(subProcessDefinition.getId())
                .joinParallelActivitiesOnComplete(false));

        tasks = taskService.createTaskQuery().processInstanceId(processInstance.getId()).orderByTaskName().asc().list();
        assertEquals(9, tasks.size());
        List<String> expectedTaskNames = Arrays.asList("Task A", "Task B", "Task C", "Task D", "five", "four", "one", "three", "two");
        for (int i=0; i<expectedTaskNames.size(); i++) {
            assertEquals(expectedTaskNames.get(i), tasks.get(i).getName());
        }

        taskService.complete(taskB.getId());
        Task afterBTask = taskService.createTaskQuery().processInstanceId(processInstance.getId()).taskDefinitionKey("afterB").singleResult();
        assertNotNull(afterBTask.getId());

        for (Task task : taskService.createTaskQuery().list()) {
            taskService.complete(task.getId());
        }

        Task afterSubProcessTask = taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
        assertEquals("After sub process", afterSubProcessTask.getName());
        taskService.complete(afterSubProcessTask.getId());
        assertProcessEnded(processInstance.getId());

        removeDerivedDeployments();
        repositoryService.deleteDeployment(deployment.getId(), true);
    }

    protected void removeDerivedDeployments() {
        for (Deployment deployment : repositoryService.createDeploymentQuery().list()) {
            if (deployment.getDerivedFrom() != null) {
                repositoryService.deleteDeployment(deployment.getId(), true);
            }
        }
    }

}