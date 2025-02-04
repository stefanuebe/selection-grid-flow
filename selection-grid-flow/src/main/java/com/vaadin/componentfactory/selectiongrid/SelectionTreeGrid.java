package com.vaadin.componentfactory.selectiongrid;

/*
 * #%L
 * Selection Grid
 * %%
 * Copyright (C) 2020 Vaadin Ltd
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
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
 * #L%
 */

import com.vaadin.flow.component.ClientCallable;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.grid.GridMultiSelectionModel;
import com.vaadin.flow.component.grid.GridSelectionModel;
import com.vaadin.flow.component.treegrid.TreeGrid;
import com.vaadin.flow.data.provider.DataProvider;
import com.vaadin.flow.data.provider.hierarchy.HierarchicalDataCommunicator;
import com.vaadin.flow.data.provider.hierarchy.HierarchicalDataProvider;
import com.vaadin.flow.data.provider.hierarchy.HierarchyMapper;
import com.vaadin.flow.data.provider.hierarchy.TreeDataProvider;
import com.vaadin.flow.data.renderer.TemplateRenderer;
import com.vaadin.flow.function.SerializableComparator;
import com.vaadin.flow.function.ValueProvider;
import com.vaadin.flow.internal.Range;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Tag("vaadin-selection-grid")
@CssImport(value = "./styles/grid.css", themeFor = "vaadin-selection-grid")
@JsModule("./src/vcf-selection-grid.js")
@JsModule("./src/selection-grid.js")
public class SelectionTreeGrid<T> extends TreeGrid<T> {

    /**
     *  @see  TreeGrid#TreeGrid()
     */
    public SelectionTreeGrid() {
        super();
    }

    /**
     * @see  TreeGrid#TreeGrid(Class)
     *
     * @param beanType beanType – the bean type to use, not null
     */
    public SelectionTreeGrid(Class<T> beanType) {
        super(beanType);
    }

    /**
     * @see  TreeGrid#TreeGrid(HierarchicalDataProvider)
     *
     * @param dataProvider dataProvider – the data provider, not null
     */
    public SelectionTreeGrid(HierarchicalDataProvider<T, ?> dataProvider) {
        super(dataProvider);
    }

    /**
     * Focus on the first cell on the row
     *
     * @param item item to scroll and focus
     */
    public void focusOnCell(T item) {
        focusOnCell(item, null);
    }

    /**
     * Focus on the specific column on the row
     *
     * @param item item to scroll and focus
     * @param column column to focus
     */
    public void focusOnCell(T item, Column<T> column) {
        expandAncestor(item);
        int index = getIndexForItem(item);
        if (index >= 0) {
            //String internalId = (column != null)?getColumnInternalId(column):"";
            int colIndex = (column != null)?getColumns().indexOf(column):0;
            this.getElement().executeJs("this.focusOnCellWhenReady($0, $1, true);", index, colIndex);
        }
    }

    /**
     * The method for scrolling to an item. Takes into account lazy loading nature
     * of grid and does the scroll operation only until the grid has finished
     * loading data
     *
     * @param item the item where to scroll to
     */
    public void scrollToItem(T item) {
        expandAncestor(item);
        int index = getIndexForItem(item);
        if (index >= 0) {
            this.getElement().executeJs("this.scrollWhenReady($0, true);", index);
        }
    }

    private List<T> expandAncestor(T item) {
        List<T> ancestors = new ArrayList<>();

        T parent = getTreeData().getParent(item);
        while (parent != null) {
            ancestors.add(parent);
            item = parent;
            parent = getTreeData().getParent(item);
        }
        if (!ancestors.isEmpty()) {
            expand(ancestors);
        }
        return ancestors;
    }

    private int getIndexForItem(T item) {
        HierarchicalDataCommunicator<T> dataCommunicator = super.getDataCommunicator();
        return dataCommunicator.getIndex(item);
    }

    @Override
    public void setDataProvider(DataProvider<T, ?> dataProvider) {
        if (!(dataProvider instanceof TreeDataProvider)) {
            throw new IllegalArgumentException("SelectionTreeGrid only accepts TreeDataProvider.");
        }
        super.setDataProvider(dataProvider);
    }

    @ClientCallable
    private void selectRange(int fromIndex, int toIndex) {
        int from = Math.min(fromIndex, toIndex);
        int to = Math.max(fromIndex, toIndex);

        HierarchicalDataCommunicator<T> dataCommunicator = super.getDataCommunicator();
        Method getHierarchyMapper ;
        try {
            getHierarchyMapper = HierarchicalDataCommunicator.class.getDeclaredMethod("getHierarchyMapper");
            getHierarchyMapper.setAccessible(true);
            HierarchyMapper<T, ?> mapper = (HierarchyMapper<T, ?>) getHierarchyMapper.invoke(dataCommunicator);
            asMultiSelect().select((mapper.fetchHierarchyItems(Range.withLength(from, to - from + 1))).collect(Collectors.toSet()));
        } catch (Exception ignored) {
        }
    }

    /**
     * Select the range and deselect the other items
     * @param fromIndex
     * @param toIndex
     */
    @ClientCallable
    private void selectRangeOnly(int fromIndex, int toIndex) {
        GridSelectionModel<T> model = getSelectionModel();
        if (model instanceof GridMultiSelectionModel) {
            int from = Math.min(fromIndex, toIndex);
            int to = Math.max(fromIndex, toIndex);
            HierarchicalDataCommunicator<T> dataCommunicator = super.getDataCommunicator();
            Method getHierarchyMapper ;
            try {
                getHierarchyMapper = HierarchicalDataCommunicator.class.getDeclaredMethod("getHierarchyMapper");
                getHierarchyMapper.setAccessible(true);
                HierarchyMapper<T, ?> mapper = (HierarchyMapper<T, ?>) getHierarchyMapper.invoke(dataCommunicator);
                Set<T> newSelectedItems = (mapper.fetchHierarchyItems(Range.withLength(from, to - from + 1))).collect(Collectors.toSet());
                HashSet<T> oldSelectedItems = new HashSet<>(getSelectedItems());
                oldSelectedItems.removeAll(newSelectedItems);
                asMultiSelect().updateSelection(newSelectedItems, oldSelectedItems);
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    protected void setSelectionModel(GridSelectionModel<T> model, SelectionMode selectionMode) {
        getElement().executeJs("if (this.querySelector('vaadin-grid-flow-selection-column')) { this.querySelector('vaadin-grid-flow-selection-column').hidden = true }");
        super.setSelectionModel(model, selectionMode);
    }

    @Override
    public Column<T> addHierarchyColumn(ValueProvider<T, ?> valueProvider) {
        Column<T> column = addColumn(TemplateRenderer
            .<T> of("<vaadin-grid-tree-toggle "
                + "leaf='[[item.leaf]]' expanded='{{expanded}}' level='[[level]]'>"
                + "</vaadin-grid-tree-toggle>[[item.name]]")
            .withProperty("leaf",
                item -> !getDataCommunicator().hasChildren(item))
            .withProperty("name",
                value -> String.valueOf(valueProvider.apply(value))));
        final SerializableComparator<T> comparator =
            (a, b) -> compareMaybeComparables(valueProvider.apply(a),
                valueProvider.apply(b));
        column.setComparator(comparator);

        return column;
    }

    /**
     * Adds theme variants to the component.
     *
     * @param variants
     *            theme variants to add
     */
    public void addThemeVariants(SelectionGridVariant... variants) {
        getThemeNames().addAll(Stream.of(variants)
                .map(SelectionGridVariant::getVariantName).collect(Collectors.toList()));
    }

    /**
     * Removes theme variants from the component.
     *
     * @param variants
     *            theme variants to remove
     */
    public void removeThemeVariants(SelectionGridVariant... variants) {
        getThemeNames().removeAll(Stream.of(variants)
                .map(SelectionGridVariant::getVariantName).collect(Collectors.toList()));
    }
}
