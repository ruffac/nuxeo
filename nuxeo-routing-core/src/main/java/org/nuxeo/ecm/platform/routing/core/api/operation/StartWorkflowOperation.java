/*
 * Copyright (c) 2012 Nuxeo SA (http://nuxeo.com/) and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     mcedica
 */
package org.nuxeo.ecm.platform.routing.core.api.operation;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.automation.core.util.Properties;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.platform.routing.api.DocumentRoutingConstants;
import org.nuxeo.ecm.platform.routing.api.DocumentRoutingService;

/**
 * Starts the workflow with the given model id on the input documents. Returns back the input documents. The id of the
 * created workflow instance is available under the \"WorkflowId\" context variable.
 *
 * @since 5.6
 */
@Operation(id = StartWorkflowOperation.ID, category = Constants.CAT_WORKFLOW, label = "Start workflow", requires = Constants.WORKFLOW_CONTEXT, description = "Starts the workflow with the given model id on the input documents. Returns back the input documents."
        + "The id of the created workflow instance is available under the \"workflowInstanceId\" context variable."
        + "@Since 5.7.2 you can set multiple variables on the workflow (before 5.8 only scalar types are supported). The variables are specified as <i>key=value</i> pairs separated by a new line."
        + "To specify multi-line values you can use a \\ character followed by a new line. <p>Example:<pre>description=foo bar</pre>For updating a date, you will need to expose the value as ISO 8601 format, "
        + "for instance : <p>Example:<pre>title=The Document Title<br>issued=@{org.nuxeo.ecm.core.schema.utils.DateParser.formatW3CDateTime(CurrentDate.date)}</pre><p>"
        + " @since 5.9.3 and 5.8.0-HF10 you can also set variables of complex types, by submiting a JSON representation: "
        + "<p><pre>assignees = [\"John Doe\", \"John Test\"]</pre></p>")
public class StartWorkflowOperation {

    public static final String ID = "Context.StartWorkflow";

    private static Log log = LogFactory.getLog(StartWorkflowOperation.class);

    @Context
    protected CoreSession session;

    @Context
    protected OperationContext ctx;

    @Param(name = "id", required = true)
    protected String id;

    @Param(name = "start", required = false, values = "true")
    protected Boolean start = true;

    // @since 5.7.2
    @Param(name = "variables", required = false)
    protected Properties variables;

    @Context
    protected DocumentRoutingService documentRoutingService;

    @OperationMethod
    public DocumentModelList run(DocumentModelList docs) {
        List<String> ids = new ArrayList<String>();
        for (DocumentModel doc : docs) {
            ids.add(doc.getId());
        }
        startNewInstance(ids);
        return docs;
    }

    @OperationMethod
    public DocumentModel run(DocumentModel doc) {
        List<String> ids = new ArrayList<String>();
        ids.add(doc.getId());
        startNewInstance(ids);
        return doc;
    }

    protected void startNewInstance(List<String> ids) {
        Map<String, Serializable> vars = new HashMap<String, Serializable>();
        if (variables != null) {
            for (Entry<String, String> entry : variables.entrySet()) {
                vars.put(entry.getKey(), entry.getValue());
            }
        }
        vars.put(DocumentRoutingConstants._MAP_VAR_FORMAT_JSON, Boolean.TRUE);
        String workflowId = documentRoutingService.createNewInstance(id, ids, vars, session, Boolean.TRUE.equals(start));
        ctx.put("WorkflowId", workflowId);
        // to be consistent with all the other workflow variablesin the context
        // @since 5.7.2
        ctx.put("workflowInstanceId", workflowId);

    }
}