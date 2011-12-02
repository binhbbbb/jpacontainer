package com.vaadin.addon.jpacontainer.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.metamodel.Attribute;
import javax.persistence.metamodel.EntityType;
import javax.persistence.metamodel.Metamodel;
import javax.persistence.metamodel.PluralAttribute;
import javax.persistence.metamodel.Type;

import com.vaadin.addon.jpacontainer.EntityContainer;
import com.vaadin.addon.jpacontainer.JPAContainer;
import com.vaadin.addon.jpacontainer.JPAContainerFactory;
import com.vaadin.addon.jpacontainer.JPAContainerItem;
import com.vaadin.addon.jpacontainer.metadata.PropertyKind;
import com.vaadin.data.Container;
import com.vaadin.data.Container.Filter;
import com.vaadin.data.Item;
import com.vaadin.data.util.BeanItem;
import com.vaadin.data.util.filter.Compare;
import com.vaadin.event.Action;
import com.vaadin.event.Action.Handler;
import com.vaadin.ui.AbstractSelect;
import com.vaadin.ui.AbstractTextField;
import com.vaadin.ui.Component;
import com.vaadin.ui.DefaultFieldFactory;
import com.vaadin.ui.Field;
import com.vaadin.ui.Form;
import com.vaadin.ui.NativeSelect;
import com.vaadin.ui.Table;
import com.vaadin.ui.TableFieldFactory;

/**
 * A helper class for JPAContainer users. E.g. automatically creates selects for
 * reference fields to another entity.
 * 
 * TODO collection types, open for extension
 */
@SuppressWarnings("rawtypes")
public class JPAContainerFieldFactory extends DefaultFieldFactory {

    private HashMap<Class<?>, String[]> propertyOrders;
    private EntityManagerPerRequestHelper entityManagerPerRequestHelper;
    private HashMap<Class<?>, Class<? extends AbstractSelect>> multiselectTypes;
    private HashMap<Class<?>, Class<? extends AbstractSelect>> singleselectTypes;

    /**
     * Creates a new JPAContainerFieldFactory. For referece/collection types
     * ComboBox or multiselects are created by default.
     */
    public JPAContainerFieldFactory() {
    }

    /**
     * Creates a new JPAContainerFieldFactory. For referece/collection types
     * ComboBox or multiselects are created by default.
     * 
     * @param emprHelper
     *            the {@link EntityManagerPerRequestHelper} to use for updating
     *            the entity manager in internally generated JPAContainers for
     *            each request.
     */
    public JPAContainerFieldFactory(EntityManagerPerRequestHelper emprHelper) {
        setEntityManagerPerRequestHelper(emprHelper);
    }

    @Override
    public Field createField(Item item, Object propertyId, Component uiContext) {
        if (item instanceof JPAContainerItem) {
            JPAContainerItem jpaitem = (JPAContainerItem) item;
            EntityContainer container = jpaitem.getContainer();

            Field field = createJPAContainerBackedField(jpaitem.getItemId(),
                    propertyId, container, uiContext);
            if (field != null) {
                return field;
            }
        }
        return configureBasicFields(super.createField(item, propertyId,
                uiContext));
    }

    /**
     * This method can be used to configure field generated by the
     * DefaultFieldFactory. By default it sets null representation of textfields
     * to empty string instead of 'null'.
     * 
     * @param field
     * @return
     */
    protected Field configureBasicFields(Field field) {
        if (field instanceof AbstractTextField) {
            ((AbstractTextField) field).setNullRepresentation("");
        }
        return field;
    }

    @Override
    public Field createField(Container container, Object itemId,
            Object propertyId, Component uiContext) {
        if (container instanceof EntityContainer) {
            EntityContainer jpacontainer = (EntityContainer) container;
            Field field = createJPAContainerBackedField(itemId, propertyId,
                    jpacontainer, uiContext);
            if (field != null) {
                return field;
            }
        }
        return configureBasicFields(super.createField(container, itemId,
                propertyId, uiContext));
    }

    private Field createJPAContainerBackedField(Object itemId,
            Object propertyId, EntityContainer jpacontainer, Component uiContext) {
        Field field = null;
        PropertyKind propertyKind = jpacontainer.getPropertyKind(propertyId);
        switch (propertyKind) {
        case MANY_TO_ONE:
            field = createReferenceSelect(jpacontainer, itemId, propertyId,
                    uiContext);
            break;
        case ONE_TO_ONE:
            field = createOneToOneField(jpacontainer, itemId, propertyId,
                    uiContext);
            break;
        case ONE_TO_MANY:
            field = createMasterDetailEditor(jpacontainer, itemId, propertyId,
                    uiContext);
            break;
        case MANY_TO_MANY:
            field = createCollectionSelect(jpacontainer, itemId, propertyId,
                    uiContext);
            break;
        default:
            break;
        }
        return field;
    }

    protected OneToOneForm createOneToOneField(EntityContainer<?> jpacontainer,
            Object itemId, Object propertyId, Component uiContext) {
        OneToOneForm oneToOneForm = new OneToOneForm();
        oneToOneForm.setBackReferenceId(jpacontainer.getEntityClass()
                .getSimpleName().toLowerCase());
        oneToOneForm.setCaption(DefaultFieldFactory
                .createCaptionByPropertyId(propertyId));
        oneToOneForm.setFormFieldFactory(this);
        if (uiContext instanceof Form) {
            // write buffering is configure by Form after binding the data
            // source. Yes, you may read the previous sentence again or verify
            // this from the Vaadin code if you don't believe what you just
            // read.
            // As oneToOneForm creates the referenced type on demand if required
            // the buffering state needs to be available when proeprty is set
            // (otherwise the original master entity will be modified once the
            // form is opened).
            Form f = (Form) uiContext;
            oneToOneForm.setWriteThrough(f.isWriteThrough());
        }
        return oneToOneForm;
    }

    @SuppressWarnings({ "serial" })
    protected Field createCollectionSelect(
            EntityContainer containerForProperty, Object itemId,
            Object propertyId, Component uiContext) {
        /*
         * Detect what kind of reference type we have
         */
        Class masterEntityClass = containerForProperty.getEntityClass();
        Class referencedType = detectReferencedType(
                getEntityManagerFactory(containerForProperty), propertyId,
                masterEntityClass);
        final JPAContainer container = createJPAContainerFor(
                containerForProperty, referencedType, false);
        final AbstractSelect select = constructCollectionSelect(
                containerForProperty, itemId, propertyId, uiContext,
                referencedType);
        select.setCaption(DefaultFieldFactory
                .createCaptionByPropertyId(propertyId));
        select.setContainerDataSource(container);
        // many to many, selectable from table listing all existing pojos
        select.setPropertyDataSource(new MultiSelectTranslator(select));
        select.setMultiSelect(true);
        if (select instanceof Table) {
            Table t = (Table) select;
            t.setSelectable(true);
            Object[] visibleProperties = getVisibleProperties(referencedType);
            if (visibleProperties == null) {
                List<Object> asList = new ArrayList<Object>(Arrays.asList(t
                        .getVisibleColumns()));
                asList.remove("id");
                // TODO this should be the true "back reference" field from the
                // opposite direction, now we expect convention
                final String backReferencePropertyId = masterEntityClass
                        .getSimpleName().toLowerCase() + "s";
                asList.remove(backReferencePropertyId);
                visibleProperties = asList.toArray();
            }
            t.setVisibleColumns(visibleProperties);
        } else {
            select.setItemCaptionMode(AbstractSelect.ITEM_CAPTION_MODE_ITEM);
        }

        return select;
    }

    @SuppressWarnings({ "serial" })
    private Field createMasterDetailEditor(
            EntityContainer containerForProperty, Object itemId,
            Object propertyId, Component uiContext) {
        // FIXME buffered mode
        Class masterEntityClass = containerForProperty.getEntityClass();
        Class referencedType = detectReferencedType(
                getEntityManagerFactory(containerForProperty), propertyId,
                masterEntityClass);
        final JPAContainer container = createJPAContainerFor(
                containerForProperty, referencedType, false);
        final Table table = new Table(
                DefaultFieldFactory.createCaptionByPropertyId(propertyId),
                container);
        // Modify container to filter only those details that relate to
        // this master data
        final String backReferencePropertyId = HibernateUtil
                .getMappedByProperty(containerForProperty.getItem(itemId)
                        .getEntity(), propertyId.toString());
        final Object masterEntity = containerForProperty.getEntityProvider()
                .getEntity(itemId);
        Filter filter = new Compare.Equal(backReferencePropertyId, masterEntity);
        container.addContainerFilter(filter);

        Object[] visibleProperties = getVisibleProperties(referencedType);
        if (visibleProperties == null) {
            List<Object> asList = new ArrayList<Object>(Arrays.asList(table
                    .getVisibleColumns()));
            asList.remove("id");
            asList.remove(backReferencePropertyId);
            visibleProperties = asList.toArray();
        }
        table.setVisibleColumns(visibleProperties);

        final Action add = new Action(getMasterDetailAddItemCaption());
        // add add and remove actions to table
        Action remove = new Action(getMasterDetailRemoveItemCaption());
        final Action[] actions = new Action[] { add, remove };

        table.addActionHandler(new Handler() {

            @SuppressWarnings("unchecked")
            public void handleAction(Action action, Object sender, Object target) {
                if (action == add) {
                    try {
                        Object newInstance = container.getEntityClass()
                                .newInstance();
                        BeanItem beanItem = new BeanItem(newInstance);
                        beanItem.getItemProperty(backReferencePropertyId)
                                .setValue(masterEntity);
                        // TODO need to update the actual property also!?
                        container.addEntity(newInstance);
                    } catch (Exception e) {
                        Logger.getLogger(getClass().getName()).warning(
                                "Could not instantiate detail instance "
                                        + container.getEntityClass().getName());
                    }
                } else {
                    table.removeItem(target);
                }
            }

            public Action[] getActions(Object target, Object sender) {
                return actions;
            }
        });
        table.setTableFieldFactory(getFieldFactoryForMasterDetailEditor());
        table.setEditable(true);

        return table;
    }

    /**
     * Detects the type entities in "collection types" (oneToMany, ManyToMany).
     * 
     * @param propertyId
     * @param masterEntityClass
     * @return the type of entities in collection type
     */
    protected Class detectReferencedType(EntityManagerFactory emf,
            Object propertyId, Class masterEntityClass) {
        Class referencedType = null;
        Metamodel metamodel = emf.getMetamodel();
        Set<EntityType<?>> entities = metamodel.getEntities();
        for (EntityType<?> entityType : entities) {
            Class<?> javaType = entityType.getJavaType();
            if (javaType == masterEntityClass) {
                Attribute<?, ?> attribute = entityType.getAttribute(propertyId
                        .toString());
                PluralAttribute pAttribute = (PluralAttribute) attribute;
                Type elementType = pAttribute.getElementType();
                referencedType = elementType.getJavaType();
                break;
            }
        }
        return referencedType;
    }

    private EntityManagerFactory getEntityManagerFactory(
            EntityContainer<?> containerForProperty) {
        return containerForProperty.getEntityProvider().getEntityManager()
                .getEntityManagerFactory();
    }

    /**
     * TODO consider opening and adding parameters like propertyId, master class
     * etc
     * 
     * @return
     */
    private TableFieldFactory getFieldFactoryForMasterDetailEditor() {
        return this;
    }

    private String getMasterDetailRemoveItemCaption() {
        return "Remove";
    }

    private String getMasterDetailAddItemCaption() {
        return "Add";
    }

    /**
     * Creates a field for simple reference (ManyToOne)
     * 
     * @param containerForProperty
     * @param propertyId
     * @return
     */
    protected Field createReferenceSelect(EntityContainer containerForProperty,
            Object itemId, Object propertyId, Component uiContext) {
        Class<?> type = containerForProperty.getType(propertyId);
        JPAContainer container = createJPAContainerFor(containerForProperty,
                type, false);

        AbstractSelect nativeSelect = constructReferenceSelect(
                containerForProperty, itemId, propertyId, uiContext, type);
        nativeSelect.setMultiSelect(false);
        nativeSelect.setCaption(DefaultFieldFactory
                .createCaptionByPropertyId(propertyId));
        nativeSelect.setItemCaptionMode(NativeSelect.ITEM_CAPTION_MODE_ITEM);
        nativeSelect.setContainerDataSource(container);
        nativeSelect.setPropertyDataSource(new SingleSelectTranslator(
                nativeSelect));
        return nativeSelect;
    }

    protected AbstractSelect constructReferenceSelect(
            EntityContainer containerForProperty, Object itemId,
            Object propertyId, Component uiContext, Class<?> type) {
        if (singleselectTypes != null) {
            Class<? extends AbstractSelect> class1 = singleselectTypes
                    .get(type);
            if (class1 != null) {
                try {
                    return class1.newInstance();
                } catch (Exception e) {
                    Logger.getLogger(getClass().getName()).warning(
                            "Could not create select of type "
                                    + class1.getName());
                }
            }
        }
        return new NativeSelect();
    }

    protected AbstractSelect constructCollectionSelect(
            EntityContainer containerForProperty, Object itemId,
            Object propertyId, Component uiContext, Class<?> type) {
        if (multiselectTypes != null) {
            Class<? extends AbstractSelect> class1 = multiselectTypes.get(type);
            try {
                return class1.newInstance();
            } catch (Exception e) {
                Logger.getLogger(getClass().getName()).warning(
                        "Could not create select of type " + class1.getName());
            }
        }
        return new Table();
    }

    protected EntityManager getEntityManagerFor(
            EntityContainer<?> containerForProperty) {
        return containerForProperty.getEntityProvider().getEntityManager();
    }

    protected JPAContainer<?> createJPAContainerFor(
            EntityContainer<?> containerForProperty, Class<?> type,
            boolean buffered) {
        JPAContainer<?> container = null;
        EntityManager em = getEntityManagerFor(containerForProperty);
        if (buffered) {
            container = JPAContainerFactory.makeBatchable(type, em);
        } else {
            container = JPAContainerFactory.make(type, em);
        }
        // Set the lazy loading delegate to the same as the parent.
        container.getEntityProvider().setLazyLoadingDelegate(
                containerForProperty.getEntityProvider()
                        .getLazyLoadingDelegate());
        if (entityManagerPerRequestHelper != null) {
            entityManagerPerRequestHelper.addContainer(container);
        }
        return container;
    }

    /**
     * Configures visible properties and their order for fields created for
     * reference/collection types referencing to given entity type. This order
     * is for example used by Table's created for OneToMany or ManyToMany
     * reference types.
     * 
     * @param containerType
     *            the entity type for which the visible properties will be set
     * @param propertyIdentifiers
     *            the identifiers in wished order to be displayed
     */
    public void setVisibleProperties(Class<?> containerType,
            String... propertyIdentifiers) {
        if (propertyOrders == null) {
            propertyOrders = new HashMap<Class<?>, String[]>();
        }
        propertyOrders.put(containerType, propertyIdentifiers);
    }

    public void setMultiSelectType(Class<?> referenceType,
            Class<? extends AbstractSelect> selectType) {
        if (multiselectTypes == null) {
            multiselectTypes = new HashMap<Class<?>, Class<? extends AbstractSelect>>();
        }
        multiselectTypes.put(referenceType, selectType);
    }

    public void setSingleSelectType(Class<?> referenceType,
            Class<? extends AbstractSelect> selectType) {
        if (singleselectTypes == null) {
            singleselectTypes = new HashMap<Class<?>, Class<? extends AbstractSelect>>();
        }
        singleselectTypes.put(referenceType, selectType);
    }

    /**
     * Returns customized visible properties (and their order) for given entity
     * type.
     * 
     * @param containerType
     * @return property identifiers that are configured to be displayed
     */
    public String[] getVisibleProperties(Class<?> containerType) {
        if (propertyOrders != null) {
            return propertyOrders.get(containerType);
        }
        return null;
    }

    /**
     * @return The {@link EntityManagerPerRequestHelper} that is used for
     *         updating the entity managers for all JPAContainers generated by
     *         this field factory.
     */
    public EntityManagerPerRequestHelper getEntityManagerPerRequestHelper() {
        return entityManagerPerRequestHelper;
    }

    /**
     * Sets the {@link EntityManagerPerRequestHelper} that is used for updating
     * the entity manager of JPAContainers generated by this field factory.
     * 
     * @param entityManagerPerRequestHelper
     */
    public void setEntityManagerPerRequestHelper(
            EntityManagerPerRequestHelper entityManagerPerRequestHelper) {
        this.entityManagerPerRequestHelper = entityManagerPerRequestHelper;
    }
}
