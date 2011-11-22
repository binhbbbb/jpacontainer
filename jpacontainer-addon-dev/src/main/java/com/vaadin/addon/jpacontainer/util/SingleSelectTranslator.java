package com.vaadin.addon.jpacontainer.util;

import com.vaadin.addon.jpacontainer.EntityContainer;
import com.vaadin.ui.AbstractSelect;

public class SingleSelectTranslator extends PropertyTranslator {
    
    private final AbstractSelect select;
    
    public SingleSelectTranslator(AbstractSelect select) {
        this.select = select;
    }
    
    private EntityContainer getContainer() {
        return (EntityContainer) select.getContainerDataSource();
    }

    @Override
    public Object translateFromDatasource(Object value) {
        // Value here is entity, should be transformed to identifier
        return getContainer().getEntityProvider().getIdentifier(value);
    }

    @Override
    public Object translateToDatasource(Object formattedValue) throws Exception {
        // formattedValue here is identifier, to be formatted to entity
        return getContainer().getEntityProvider().getEntity(formattedValue);
    }

}