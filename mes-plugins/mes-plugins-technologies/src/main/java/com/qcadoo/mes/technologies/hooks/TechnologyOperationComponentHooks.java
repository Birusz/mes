/**
 * ***************************************************************************
 * Copyright (c) 2010 Qcadoo Limited
 * Project: Qcadoo MES
 * Version: 1.4
 *
 * This file is part of Qcadoo.
 *
 * Qcadoo is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation; either version 3 of the License,
 * or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 * ***************************************************************************
 */
package com.qcadoo.mes.technologies.hooks;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.qcadoo.mes.technologies.TechnologyService;
import com.qcadoo.mes.technologies.constants.AssignedToOperation;
import com.qcadoo.mes.technologies.constants.OperationFields;
import com.qcadoo.mes.technologies.constants.OperationProductOutComponentFields;
import com.qcadoo.mes.technologies.constants.ProductStructureTreeNodeFields;
import com.qcadoo.mes.technologies.constants.TechnologiesConstants;
import com.qcadoo.mes.technologies.constants.TechnologyFields;
import com.qcadoo.mes.technologies.constants.TechnologyOperationComponentFields;
import com.qcadoo.model.api.DataDefinition;
import com.qcadoo.model.api.DataDefinitionService;
import com.qcadoo.model.api.Entity;
import com.qcadoo.model.api.EntityList;
import com.qcadoo.model.api.EntityTree;
import com.qcadoo.model.api.EntityTreeNode;
import com.qcadoo.model.api.search.SearchRestrictions;

@Service
public class TechnologyOperationComponentHooks {

    @Autowired
    private TechnologyService technologyService;

    @Autowired
    private DataDefinitionService dataDefinitionService;

    public void onCreate(final DataDefinition technologyOperationComponentDD, final Entity technologyOperationComponent) {
        copyCommentAndAttachmentFromOperation(technologyOperationComponent);
        setParentIfRootNodeAlreadyExists(technologyOperationComponent);
        setOperationOutProduct(technologyOperationComponent);
        copyReferencedTechnology(technologyOperationComponentDD, technologyOperationComponent);
        copyWorkstationsSettingsFromOperation(technologyOperationComponent);
    }

    public void copyWorkstationsSettingsFromOperation(final Entity technologyOperationComponent) {
        Entity operation = technologyOperationComponent.getBelongsToField(TechnologyOperationComponentFields.OPERATION);

        if (Objects.nonNull(operation)) {
            technologyOperationComponent.setField(TechnologyOperationComponentFields.QUANTITY_OF_WORKSTATIONS,
                    operation.getIntegerField(OperationFields.QUANTITY_OF_WORKSTATIONS));
            technologyOperationComponent.setField(TechnologyOperationComponentFields.ASSIGNED_TO_OPERATION,
                    operation.getField(OperationFields.ASSIGNED_TO_OPERATION));
            technologyOperationComponent.setField(TechnologyOperationComponentFields.WORKSTATION_TYPE,
                    operation.getBelongsToField(OperationFields.WORKSTATION_TYPE));
            technologyOperationComponent.setField(TechnologyOperationComponentFields.WORKSTATIONS,
                    operation.getManyToManyField(OperationFields.WORKSTATIONS));
            technologyOperationComponent.setField(TechnologyOperationComponentFields.DIVISION,
                    operation.getBelongsToField(OperationFields.DIVISION));
            technologyOperationComponent.setField(TechnologyOperationComponentFields.PRODUCTION_LINE,
                    operation.getBelongsToField(OperationFields.PRODUCTION_LINE));
        }
    }

    private void copyCommentAndAttachmentFromOperation(final Entity technologyOperationComponent) {
        technologyService.copyCommentAndAttachmentFromLowerInstance(technologyOperationComponent,
                TechnologyOperationComponentFields.OPERATION);
    }

    private void setParentIfRootNodeAlreadyExists(final Entity technologyOperationComponent) {
        Entity technology = technologyOperationComponent.getBelongsToField(TechnologyOperationComponentFields.TECHNOLOGY);
        EntityTree tree = technology.getTreeField(TechnologyFields.OPERATION_COMPONENTS);

        if (Objects.isNull(tree) || tree.isEmpty()) {
            return;
        }

        EntityTreeNode rootNode = tree.getRoot();

        if (Objects.isNull(rootNode)
                || Objects.nonNull(technologyOperationComponent.getBelongsToField(TechnologyOperationComponentFields.PARENT))) {
            return;
        }

        technologyOperationComponent.setField(TechnologyOperationComponentFields.PARENT, rootNode);
    }

    private void setOperationOutProduct(final Entity technologyOperationComponent) {
        if (Objects.nonNull(
                technologyOperationComponent.getHasManyField(TechnologyOperationComponentFields.OPERATION_PRODUCT_OUT_COMPONENTS))
                && technologyOperationComponent
                        .getHasManyField(TechnologyOperationComponentFields.OPERATION_PRODUCT_OUT_COMPONENTS).isEmpty()) {
            Entity technology = technologyOperationComponent.getBelongsToField(TechnologyOperationComponentFields.TECHNOLOGY);
            EntityTree tree = technology.getTreeField(TechnologyFields.OPERATION_COMPONENTS);

            Entity operationProductOutComponent = getOperationProductOutComponentDD().create();

            operationProductOutComponent.setField(OperationProductOutComponentFields.QUANTITY, 1);

            if (Objects.isNull(tree) || tree.isEmpty()) {
                operationProductOutComponent.setField(OperationProductOutComponentFields.PRODUCT,
                        technology.getBelongsToField(TechnologyFields.PRODUCT));

                technologyOperationComponent.setField(TechnologyOperationComponentFields.OPERATION_PRODUCT_OUT_COMPONENTS,
                        Collections.singletonList(operationProductOutComponent));
            } else {
                Entity operation = technologyOperationComponent.getBelongsToField(TechnologyOperationComponentFields.OPERATION);

                if (Objects.nonNull(operation)) {
                    Entity product = operation.getBelongsToField(OperationFields.PRODUCT);

                    if (Objects.nonNull(product)) {
                        operationProductOutComponent.setField(OperationProductOutComponentFields.PRODUCT, product);

                        technologyOperationComponent.setField(TechnologyOperationComponentFields.OPERATION_PRODUCT_OUT_COMPONENTS,
                                Collections.singletonList(operationProductOutComponent));
                    }
                }
            }
        }
    }

    private void copyReferencedTechnology(final DataDefinition technologyOperationComponentDD,
            final Entity technologyOperationComponent) {
        if (Objects.isNull(technologyOperationComponent.getField(TechnologyOperationComponentFields.REFERENCE_TECHNOLOGY))) {
            return;
        }

        Entity technology = technologyOperationComponent.getBelongsToField(TechnologyOperationComponentFields.TECHNOLOGY);
        Entity referencedTechnology = technologyOperationComponent
                .getBelongsToField(TechnologyOperationComponentFields.REFERENCE_TECHNOLOGY);

        Set<Long> technologies = Sets.newHashSet();
        technologies.add(technology.getId());

        boolean isCyclic = checkForCyclicReferences(technologies, referencedTechnology);

        if (isCyclic) {
            technologyOperationComponent.addError(
                    technologyOperationComponentDD.getField(TechnologyOperationComponentFields.REFERENCE_TECHNOLOGY),
                    "technologies.technologyReferenceTechnologyComponent.error.cyclicDependency");

            return;
        }

        EntityTreeNode root = referencedTechnology.getTreeField(TechnologyFields.OPERATION_COMPONENTS).getRoot();

        if (Objects.isNull(root)) {
            technologyOperationComponent.addError(
                    technologyOperationComponentDD.getField(TechnologyOperationComponentFields.REFERENCE_TECHNOLOGY),
                    "technologies.technologyReferenceTechnologyComponent.error.operationComponentsEmpty");

            return;
        }

        Entity copiedRoot = copyReferencedTechnologyOperations(root,
                technologyOperationComponent.getBelongsToField(TechnologyOperationComponentFields.TECHNOLOGY));

        for (Entry<String, Object> entry : copiedRoot.getFields().entrySet()) {
            if (!(entry.getKey().equals("id") || entry.getKey().equals(TechnologyOperationComponentFields.PARENT))) {
                technologyOperationComponent.setField(entry.getKey(), entry.getValue());
            }
        }

        technologyOperationComponent.setField(TechnologyOperationComponentFields.REFERENCE_TECHNOLOGY, null);
    }

    private Entity copyReferencedTechnologyOperations(final Entity node, final Entity technology) {
        Entity copy = node.copy();

        copy.setId(null);
        copy.setField(TechnologyOperationComponentFields.PARENT, null);
        copy.setField(TechnologyOperationComponentFields.TECHNOLOGY, technology);

        for (Entry<String, Object> entry : node.getFields().entrySet()) {
            Object value = entry.getValue();

            if (value instanceof EntityList) {
                EntityList entities = (EntityList) value;
                List<Entity> copies = Lists.newArrayList();
                if (entry.getKey().equals(TechnologyOperationComponentFields.CHILDREN)) {
                    for (Entity entity : entities) {
                        copies.add(copyReferencedTechnologyOperations(entity, technology));
                    }
                } else {
                    for (Entity entity : entities) {
                        Entity fieldCopy = entity.copy();
                        fieldCopy.setId(null);
                        copies.add(fieldCopy);
                    }
                }

                copy.setField(entry.getKey(), copies);
            }
        }

        copy.setField("productionCountingQuantities", null);
        copy.setField("productionCountingOperationRuns", null);
        copy.setField("operationalTasks", null);
        copy.setField("operCompTimeCalculations", null);
        copy.setField("barcodeOperationComponents", null);

        return copy;
    }

    private boolean checkForCyclicReferences(final Set<Long> technologies, final Entity referencedTechnology) {
        return technologies.contains(referencedTechnology.getId());

    }

    public void onSave(final DataDefinition technologyOperationComponentDD, final Entity technologyOperationComponent) {
        clearField(technologyOperationComponent);

        Long technologyOperationComponentId = technologyOperationComponent.getId();

        if (Objects.nonNull(technologyOperationComponentId)) {
            copyWorkstations(technologyOperationComponentDD, technologyOperationComponent);

            setTechnologicalProcessListAssignDate(technologyOperationComponentDD, technologyOperationComponent,
                    technologyOperationComponentId);
        }
    }

    private void setTechnologicalProcessListAssignDate(final DataDefinition technologyOperationComponentDD,
            final Entity technologyOperationComponent, final Long technologyOperationComponentId) {
        Entity technologyOperationComponentFromDB = technologyOperationComponentDD.get(technologyOperationComponentId);

        Date technologicalProcessListAssignmentDate = technologyOperationComponent
                .getDateField(TechnologyOperationComponentFields.TECHNOLOGICAL_PROCESS_LIST_ASSIGNMENT_DATE);
        Entity technologicalProcessList = technologyOperationComponent
                .getBelongsToField(TechnologyOperationComponentFields.TECHNOLOGICAL_PROCESS_LIST);
        Entity technologicalProcessListFromDB = technologyOperationComponentFromDB
                .getBelongsToField(TechnologyOperationComponentFields.TECHNOLOGICAL_PROCESS_LIST);

        boolean areSame = (Objects.isNull(technologicalProcessList) ? Objects.isNull(technologicalProcessListFromDB)
                : (Objects.nonNull(technologicalProcessListFromDB)
                        && technologicalProcessList.getId().equals(technologicalProcessListFromDB.getId())));

        if (Objects.nonNull(technologicalProcessList)) {
            if (Objects.isNull(technologicalProcessListAssignmentDate) || !areSame) {
                technologyOperationComponent.setField(
                        TechnologyOperationComponentFields.TECHNOLOGICAL_PROCESS_LIST_ASSIGNMENT_DATE, DateTime.now().toDate());
            }
        } else {
            technologyOperationComponent.setField(TechnologyOperationComponentFields.TECHNOLOGICAL_PROCESS_LIST_ASSIGNMENT_DATE,
                    null);
        }
    }

    private void copyWorkstations(final DataDefinition technologyOperationComponentDD,
            final Entity technologyOperationComponent) {
        Entity oldToc = technologyOperationComponentDD.get(technologyOperationComponent.getId());
        Entity operation = technologyOperationComponent.getBelongsToField(TechnologyOperationComponentFields.OPERATION);

        if (Objects.nonNull(operation)
                && !operation.getId().equals(oldToc.getBelongsToField(TechnologyOperationComponentFields.OPERATION).getId())) {

            technologyOperationComponent.setField(TechnologyOperationComponentFields.WORKSTATIONS,
                    operation.getManyToManyField(TechnologyOperationComponentFields.WORKSTATIONS));
        }
    }

    private void clearField(final Entity technologyOperationComponent) {
        String assignedToOperation = technologyOperationComponent
                .getStringField(TechnologyOperationComponentFields.ASSIGNED_TO_OPERATION);

        if (AssignedToOperation.WORKSTATIONS_TYPE.getStringValue().equals(assignedToOperation)) {
            technologyOperationComponent.setField(TechnologyOperationComponentFields.WORKSTATIONS, null);
        }
    }

    public boolean onDelete(final DataDefinition technologyOperationComponentDD, final Entity technologyOperationComponent) {
        List<Entity> usageInProductStructureTree = dataDefinitionService
                .get(TechnologiesConstants.PLUGIN_IDENTIFIER, TechnologiesConstants.MODEL_PRODUCT_STRUCTURE_TREE_NODE).find()
                .add(SearchRestrictions.belongsTo(ProductStructureTreeNodeFields.OPERATION, technologyOperationComponent)).list()
                .getEntities();

        if (!usageInProductStructureTree.isEmpty()) {
            technologyOperationComponent.addGlobalError(
                    "technologies.technologyDetails.window.treeTab.technologyTree.error.cannotDeleteOperationUsedInProductStructureTree",
                    false,
                    usageInProductStructureTree.stream()
                            .map(e -> e.getBelongsToField(ProductStructureTreeNodeFields.MAIN_TECHNOLOGY)
                                    .getStringField(TechnologyFields.NUMBER))
                            .distinct().collect(Collectors.joining(", ")));

            return false;
        }

        return true;
    }

    private DataDefinition getOperationProductOutComponentDD() {
        return dataDefinitionService.get(TechnologiesConstants.PLUGIN_IDENTIFIER,
                TechnologiesConstants.MODEL_OPERATION_PRODUCT_OUT_COMPONENT);
    }

}
