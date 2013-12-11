/*
 * Copyright (C) 2013 salesforce.com, inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
({
      goToServer : function(controller, component, event, cmpName, inValue) {
        var a = component.get("c.echo" + cmpName);

        a.setParams({
            url : inValue,
            branchName: component.get("v.branchName")
        });

        $A.log("Going to server...");
        component.find("spinner").getAttributes().setValue("isVisible", true);
        a.setCallback(component, function(action){
            if (action.getState() === "SUCCESS") {
            	$A.log("Success!\nValue from server:");
            	component.find("spinner").getAttributes().setValue("isVisible", false);
                var retValue = action.getReturnValue();
                var model = component.getModel();
                $A.log(retValue);
                if(retValue){
                    model.getValue("currentFailCount").setValue(retValue.length.toString());
                    model.getValue("testsHistory").setValue(retValue);
                	component.find("outText").getAttributes().setValue("value", "");
                }
                else{
                	component.find("outText").getAttributes().setValue("value", "No Build History Found!");
                }
            } else {
                $A.log("Fail: " + action.getError()[0].message);
            }
        });

        $A.enqueueAction(a);
    }
})
