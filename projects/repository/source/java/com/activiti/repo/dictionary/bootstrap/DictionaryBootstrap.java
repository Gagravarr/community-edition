package com.activiti.repo.dictionary.bootstrap;

import java.io.File;

import org.apache.lucene.analysis.standard.StandardAnalyzer;

import com.activiti.repo.dictionary.ClassRef;
import com.activiti.repo.dictionary.DictionaryException;
import com.activiti.repo.dictionary.NamespaceService;
import com.activiti.repo.dictionary.PropertyTypeDefinition;
import com.activiti.repo.dictionary.metamodel.M2Aspect;
import com.activiti.repo.dictionary.metamodel.M2Association;
import com.activiti.repo.dictionary.metamodel.M2ChildAssociation;
import com.activiti.repo.dictionary.metamodel.M2Class;
import com.activiti.repo.dictionary.metamodel.M2NamespacePrefix;
import com.activiti.repo.dictionary.metamodel.M2NamespaceURI;
import com.activiti.repo.dictionary.metamodel.M2Property;
import com.activiti.repo.dictionary.metamodel.M2PropertyType;
import com.activiti.repo.dictionary.metamodel.M2Type;
import com.activiti.repo.dictionary.metamodel.MetaModelDAO;
import com.activiti.repo.dictionary.metamodel.NamespaceDAO;
import com.activiti.repo.dictionary.metamodel.emf.EMFMetaModelDAO;
import com.activiti.repo.dictionary.metamodel.emf.EMFNamespaceDAO;
import com.activiti.repo.dictionary.metamodel.emf.EMFResource;
import com.activiti.repo.lock.LockService;
import com.activiti.repo.ref.QName;
import com.activiti.repo.version.Version;
import com.activiti.repo.version.VersionService;
import com.activiti.repo.version.lightweight.VersionStoreConst;
import com.activiti.util.debug.CodeMonkey;

/**
 * Provides support for creating initial set of meta-data.
 * 
 * @author David Caruana
 */
public class DictionaryBootstrap
{   
    // Base type constants
    public static final QName TYPE_QNAME_BASE = QName.createQName(NamespaceService.ACTIVITI_URI, "base");
    public static final ClassRef TYPE_BASE = new ClassRef(TYPE_QNAME_BASE);
    
    // Referenceable aspect constants
    public static final QName TYPE_QNAME_REFERENCE = QName.createQName(NamespaceService.ACTIVITI_URI, "reference");
    public static final ClassRef TYPE_REFERENCE = new ClassRef(TYPE_QNAME_REFERENCE);
    public static final String PROP_REFERENCE = "reference";
    public static final QName PROP_QNAME_REFERENCE = QName.createQName(NamespaceService.ACTIVITI_URI, PROP_REFERENCE);
        
    // Container type constants
    public static final QName TYPE_QNAME_CONTAINER = QName.createQName(NamespaceService.ACTIVITI_URI, "container");
    public static final ClassRef TYPE_CONTAINER = new ClassRef(TYPE_QNAME_CONTAINER);
    public static final QName CHILD_ASSOC_CONTENTS = QName.createQName(NamespaceService.ACTIVITI_URI, "contents");

    // Content aspect constants
    public static final QName ASPECT_QNAME_CONTENT = QName.createQName(NamespaceService.ACTIVITI_URI, "aspect_content");
    public static final String PROP_ENCODING = "encoding";
    public static final String PROP_MIME_TYPE = "mimetype";
    
    // Categories and roots
    
    public static final QName ASPECT_QNAME_ROOT = QName.createQName(NamespaceService.ACTIVITI_URI, "aspect_root");
    public static final QName TYPE_QNAME_CATEGORY = QName.createQName(NamespaceService.ACTIVITI_URI, "category");
    public static final QName TYPE_QNAME_STOREROOT = QName.createQName(NamespaceService.ACTIVITI_URI, "store_root");
    public static final QName TYPE_QNAME_CATEGORYROOT = QName.createQName(NamespaceService.ACTIVITI_URI, "category_root");
    public static final ClassRef ASPECT_CONTENT = new ClassRef(ASPECT_QNAME_CONTENT);
    public static final ClassRef ASPECT_ROOT = new ClassRef(ASPECT_QNAME_ROOT);
    public static final ClassRef TYPE_CATEGORY = new ClassRef(TYPE_QNAME_CATEGORY);
    public static final ClassRef TYPE_STOREROOT = new ClassRef(TYPE_QNAME_STOREROOT);
    public static final ClassRef TYPE_CATEGORYROOT = new ClassRef(TYPE_QNAME_CATEGORYROOT);
    

    // Content type constants
    public static final QName TYPE_QNAME_CONTENT = QName.createQName(NamespaceService.ACTIVITI_URI, "content");
    public static final ClassRef TYPE_CONTENT = new ClassRef(TYPE_QNAME_CONTENT);
 
    // expected application types
    public static final QName TYPE_QNAME_FOLDER = QName.createQName(NamespaceService.ACTIVITI_URI, "folder");
    public static final QName TYPE_QNAME_FILE = QName.createQName(NamespaceService.ACTIVITI_URI, "file");
    public static final ClassRef TYPE_FOLDER = new ClassRef(TYPE_QNAME_FOLDER);
    public static final ClassRef TYPE_FILE = new ClassRef(TYPE_QNAME_FILE);
    public static final QName ASPECT_QNAME_SPACE = QName.createQName(NamespaceService.ACTIVITI_URI, "space");
    public static final ClassRef ASPECT_SPACE = new ClassRef(ASPECT_QNAME_SPACE);
    public static final String PROP_CREATED_DATE = "createddate";
    public static final String PROP_MODIFIED_DATE = "modifieddate";
    public static final String PROP_DESCRIPTION = "description";
    public static final String PROP_ICON = "icon";
    public static final String PROP_SPACE_TYPE = "spacetype";

    // test types
    public static final QName TEST_TYPE_QNAME_FOLDER = QName.createQName(NamespaceService.ACTIVITI_TEST_URI, "folder");
    public static final QName TEST_TYPE_QNAME_FILE = QName.createQName(NamespaceService.ACTIVITI_TEST_URI, "file");
    public static final ClassRef TEST_TYPE_FOLDER = new ClassRef(TEST_TYPE_QNAME_FOLDER);
    public static final ClassRef TEST_TYPE_FILE = new ClassRef(TEST_TYPE_QNAME_FILE);

    private NamespaceDAO namespaceDAO = null;
    private MetaModelDAO metaModelDAO = null;

    /**
     * @param namespaceDAO  the namespace DAO to bootstrap with
     */
    public void setNamespaceDAO(NamespaceDAO namespaceDAO)
    {
        this.namespaceDAO = namespaceDAO;
    }
    
    /**
     * @param metaModelDAO  the meta model DAO to bootstrap with
     */
    public void setMetaModelDAO(MetaModelDAO metaModelDAO)
    {
        this.metaModelDAO = metaModelDAO;
    }
    
    /**
     * Run this to generate the bootstrap dictionary model to a temp file, which can
     * be copied to the default resource location
     * 
     * @see com.activiti.repo.dictionary.metamodel.emf.EMFResource#DEFAULT_RESOURCEURI
     * @see #bootstrapModel()
     */
    public static void main(String[] args) throws Exception
    {
        // Construct Bootstrap Service
        EMFResource resource = new EMFResource();
        File tempFile = File.createTempFile("dictionary", ".xml");
        resource.setURI(tempFile.getAbsolutePath());
        resource.initCreate();

        // Construct EMF Namespace DAO
        EMFNamespaceDAO namespaceDao = new EMFNamespaceDAO();
        namespaceDao.setResource(resource);
        namespaceDao.init();
        
        // Construct EMF Meta Model DAO
        EMFMetaModelDAO metaModelDao = new EMFMetaModelDAO();
        metaModelDao.setResource(resource);
        metaModelDao.init();

        // Construct Bootstrap Service
        DictionaryBootstrap bootstrap = new DictionaryBootstrap();
        bootstrap.setNamespaceDAO(namespaceDao);
        bootstrap.setMetaModelDAO(metaModelDao);

        // Create test Bootstrap definitions
        bootstrap.bootstrapModel();
        
        // save it
        resource.save();
        
        // report
        System.out.print("Generated dictionary model: \n" +
                "   file: " + tempFile + "\n" +
                "   default bootstrap location is: " + EMFResource.DEFAULT_RESOURCEURI);
    }

    /**
     * Loads the dictionary model into memory
     */
    public void bootstrapModel()
    {
        CodeMonkey.todo("Read model from a configuration file"); // TODO
        if (namespaceDAO == null)
        {
            throw new DictionaryException("Namespace DAO has not been provided");
        }
        if (metaModelDAO == null)
        {
            throw new DictionaryException("Meta Model DAO has not been provided");
        }
        createNamespaces();
        createPropertyTypes();
        createMetaModel();
        createVersionModel();
    }
    
    /**
     * Loads the test dictionary model into memory
     */
    public void bootstrapTestModel()
    {
        if (namespaceDAO == null)
        {
            throw new DictionaryException("Namespace DAO has not been provided");
        }
        if (metaModelDAO == null)
        {
            throw new DictionaryException("Meta Model DAO has not been provided");
        }
        createNamespaces();
        createPropertyTypes();
        createTestModel();
        CodeMonkey.todo("Load up additional (user-defined) types here"); // TODO
    }
    
    /**
     * Create bootstrap Namespace definitions
     */
    private void createNamespaces()
    {
        // Default Namespace
        M2NamespaceURI defaultURI = namespaceDAO.createURI(NamespaceService.DEFAULT_URI);
        M2NamespacePrefix defaultPrefix = namespaceDAO.createPrefix(NamespaceService.DEFAULT_PREFIX);
        defaultPrefix.setURI(defaultURI);
        
        // Activiti Namespace
        M2NamespaceURI activitiURI = namespaceDAO.createURI(NamespaceService.ACTIVITI_URI);
        M2NamespacePrefix activitiPrefix = namespaceDAO.createPrefix(NamespaceService.ACTIVITI_PREFIX);
        activitiPrefix.setURI(activitiURI);
        
        // Activiti Test Namespace
        M2NamespaceURI activitiTestURI = namespaceDAO.createURI(NamespaceService.ACTIVITI_TEST_URI);
        M2NamespacePrefix activitiTestPrefix = namespaceDAO.createPrefix(NamespaceService.ACTIVITI_TEST_PREFIX);
        activitiTestPrefix.setURI(activitiTestURI);
    }
    
    
    /**
     * Create bootstrap Property Type definitions.
     */
    private void createPropertyTypes()
    {
        M2PropertyType ANY = metaModelDAO.createPropertyType(PropertyTypeDefinition.ANY);
        ANY.setAnalyserClassName(StandardAnalyzer.class.getName());
        M2PropertyType TEXT = metaModelDAO.createPropertyType(PropertyTypeDefinition.TEXT);
        TEXT.setAnalyserClassName(StandardAnalyzer.class.getName());
        M2PropertyType CONTENT = metaModelDAO.createPropertyType(PropertyTypeDefinition.CONTENT);
        CONTENT.setAnalyserClassName(StandardAnalyzer.class.getName());
        M2PropertyType INT = metaModelDAO.createPropertyType(PropertyTypeDefinition.INT);
        INT.setAnalyserClassName(StandardAnalyzer.class.getName());
        M2PropertyType LONG = metaModelDAO.createPropertyType(PropertyTypeDefinition.LONG);
        LONG.setAnalyserClassName(StandardAnalyzer.class.getName());
        M2PropertyType FLOAT = metaModelDAO.createPropertyType(PropertyTypeDefinition.FLOAT);
        FLOAT.setAnalyserClassName(StandardAnalyzer.class.getName());
        M2PropertyType DOUBLE = metaModelDAO.createPropertyType(PropertyTypeDefinition.DOUBLE);
        DOUBLE.setAnalyserClassName(StandardAnalyzer.class.getName());
        M2PropertyType DATE = metaModelDAO.createPropertyType(PropertyTypeDefinition.DATE);
        DATE.setAnalyserClassName(StandardAnalyzer.class.getName());
        M2PropertyType DATETIME = metaModelDAO.createPropertyType(PropertyTypeDefinition.DATETIME);
        DATETIME.setAnalyserClassName(StandardAnalyzer.class.getName());
        M2PropertyType BOOLEAN = metaModelDAO.createPropertyType(PropertyTypeDefinition.BOOLEAN);
        BOOLEAN.setAnalyserClassName(StandardAnalyzer.class.getName());
        M2PropertyType NAME = metaModelDAO.createPropertyType(PropertyTypeDefinition.NAME);
        NAME.setAnalyserClassName(StandardAnalyzer.class.getName());
        M2PropertyType GUID = metaModelDAO.createPropertyType(PropertyTypeDefinition.GUID);
        GUID.setAnalyserClassName(StandardAnalyzer.class.getName());
        M2PropertyType CATEGORY = metaModelDAO.createPropertyType(PropertyTypeDefinition.CATEGORY);
        CATEGORY.setAnalyserClassName(StandardAnalyzer.class.getName());
    }
    

    /**
     * Create boostrap definitions that describe the Dictionary M2 meta-model.
     */
    private void createMetaModel()
    {
//        // Create Test Referencable Aspect
//        M2Aspect referenceAspect = metaModelDAO.createAspect(QName.createQName(NamespaceService.ACTIVITI_URI, "referenceable"));
//        M2Property idProp = referenceAspect.createProperty("id");
//        idProp.setType(metaModelDAO.getPropertyType(PropertyTypeDefinition.GUID));
//        idProp.setMandatory(true);
//        idProp.setProtected(false);
//        idProp.setMultiValued(false);
        CodeMonkey.issue("We persist the node ID as a native property - is Referencable required?"); // TODO
        
        // Create content aspect
        M2Aspect contentAspect = metaModelDAO.createAspect(ASPECT_QNAME_CONTENT);
        M2Property encodingProp = contentAspect.createProperty(PROP_ENCODING);
        encodingProp.setType(metaModelDAO.getPropertyType(PropertyTypeDefinition.TEXT));
        encodingProp.setMandatory(true);
        encodingProp.setMultiValued(false);
        M2Property mimetypeProp = contentAspect.createProperty(PROP_MIME_TYPE);
        mimetypeProp.setType(metaModelDAO.getPropertyType(PropertyTypeDefinition.TEXT));
        mimetypeProp.setMandatory(true);
        mimetypeProp.setMultiValued(false);

        // Root Aspect
        
        M2Aspect rootAspect = metaModelDAO.createAspect(ASPECT_QNAME_ROOT);
        
        // Create Test Base Type
        M2Type baseType = metaModelDAO.createType(TYPE_QNAME_BASE);
//        M2Property primaryTypeProp = baseType.createProperty("primaryType");
//        primaryTypeProp.setType(metaModelDAO.getPropertyType(PropertyTypeDefinition.NAME));
//        primaryTypeProp.setMandatory(true);
//        primaryTypeProp.setProtected(true);
//        primaryTypeProp.setMultiValued(false);
//        M2Property aspectsProp = baseType.createProperty("aspects");
//        aspectsProp.setType(metaModelDAO.getPropertyType(PropertyTypeDefinition.NAME));
//        aspectsProp.setMandatory(false);
//        aspectsProp.setProtected(true);
//        aspectsProp.setMultiValued(true);
        CodeMonkey.issue("We persist type and aspects as native properties - are these properties required?");

        // Create Reference Type
        M2Type referenceType = metaModelDAO.createType(TYPE_QNAME_REFERENCE);
        referenceType.setSuperClass(baseType);
        M2Property referenceProp = referenceType.createProperty(PROP_REFERENCE);
        referenceProp.setType(metaModelDAO.getPropertyType(PropertyTypeDefinition.ANY));
        referenceProp.setMandatory(true);
        referenceProp.setMultiValued(false);

        // Create Content Type
        CodeMonkey.todo("Need to add description to the type definition"); // TODO
        M2Type contentType = metaModelDAO.createType(TYPE_QNAME_CONTENT);
        contentType.setSuperClass(baseType);
        contentType.getDefaultAspects().add(contentAspect);
        
        // Create Container Type
        M2Type containerType = metaModelDAO.createType(TYPE_QNAME_CONTAINER);
        containerType.setSuperClass(baseType);

        M2ChildAssociation contentsAssoc = containerType.createChildAssociation("*");
        contentsAssoc.getRequiredToClasses().add(baseType);
        contentsAssoc.setMandatory(false);
        contentsAssoc.setMultiValued(true);

        // Create File Type
        M2Type fileType = metaModelDAO.createType(TYPE_QNAME_FILE);
        fileType.setSuperClass(contentType);
        
        // Create Folder Type
        M2Type folderType = metaModelDAO.createType(TYPE_QNAME_FOLDER);
        folderType.setSuperClass(baseType);
        M2ChildAssociation filesAssoc = folderType.createChildAssociation("*");
        filesAssoc.getRequiredToClasses().add(fileType);
        filesAssoc.setMandatory(false);
        filesAssoc.setMultiValued(true);
        M2ChildAssociation foldersAssoc = folderType.createChildAssociation("*");
        foldersAssoc.getRequiredToClasses().add(fileType);
        foldersAssoc.setMandatory(false);
        foldersAssoc.setMultiValued(true);
        
        // Create Category Type
        
        M2Type categoryType = metaModelDAO.createType(TYPE_QNAME_CATEGORY);
        categoryType.setSuperClass(baseType);
        M2ChildAssociation subCategoriesAssoc = categoryType.createChildAssociation("*");
        subCategoriesAssoc.getRequiredToClasses().add(baseType);
        subCategoriesAssoc.setMandatory(false);
        subCategoriesAssoc.setMultiValued(true);
        
        // Store Root
        
        M2Type storeRootType = metaModelDAO.createType(TYPE_QNAME_STOREROOT);
        storeRootType.setSuperClass(containerType);
        storeRootType.getDefaultAspects().add(rootAspect);
        
        // Create Category root Type
        M2Type categoryRootType = metaModelDAO.createType(TYPE_QNAME_CATEGORYROOT);
        categoryRootType.setSuperClass(baseType);
        categoryRootType.getDefaultAspects().add(rootAspect);
        M2ChildAssociation categoryAssoc = categoryRootType.createChildAssociation("*");
        categoryAssoc.getRequiredToClasses().add(categoryType);
        categoryAssoc.setMandatory(false);
        categoryAssoc.setMultiValued(true);
        
        // Create Space Aspect
        M2Aspect spaceAspect = metaModelDAO.createAspect(ASPECT_QNAME_SPACE);
        // created date property
        M2Property createdDateProp = spaceAspect.createProperty(PROP_CREATED_DATE);
        createdDateProp.setType(metaModelDAO.getPropertyType(PropertyTypeDefinition.DATETIME));
        createdDateProp.setMandatory(true);
        createdDateProp.setMultiValued(false);
        // modified date property
        M2Property modifiedDateProp = spaceAspect.createProperty(PROP_MODIFIED_DATE);
        modifiedDateProp.setType(metaModelDAO.getPropertyType(PropertyTypeDefinition.DATETIME));
        modifiedDateProp.setMandatory(true);
        modifiedDateProp.setMultiValued(false);
        // space type property
        M2Property spaceTypeProp = spaceAspect.createProperty(PROP_SPACE_TYPE);
        spaceTypeProp.setType(metaModelDAO.getPropertyType(PropertyTypeDefinition.TEXT));
        spaceTypeProp.setMandatory(true);
        spaceTypeProp.setMultiValued(false);
        // icon property
        M2Property iconProp = spaceAspect.createProperty(PROP_ICON);
        iconProp.setType(metaModelDAO.getPropertyType(PropertyTypeDefinition.TEXT));
        iconProp.setMandatory(true);
        iconProp.setMultiValued(false);
        // description property
        M2Property descriptionProp = spaceAspect.createProperty(PROP_DESCRIPTION);
        descriptionProp.setType(metaModelDAO.getPropertyType(PropertyTypeDefinition.TEXT));
        descriptionProp.setMandatory(false);
        descriptionProp.setMultiValued(false);
    }
    
    /**
     * Create a simple test model.
     */
    private void createTestModel()
    {
        // Create Test Referencable Aspect
        M2Aspect referenceAspect = metaModelDAO.createAspect(QName.createQName(NamespaceService.ACTIVITI_TEST_URI, "referenceable"));
        M2Property idProp = referenceAspect.createProperty("id");
        idProp.setType(metaModelDAO.getPropertyType(PropertyTypeDefinition.GUID));
        idProp.setMandatory(true);
        idProp.setProtected(false);
        idProp.setMultiValued(false);
        
        // Create Test Base Type
        M2Type baseType = metaModelDAO.createType(TYPE_QNAME_BASE);
        M2Property primaryTypeProp = baseType.createProperty("primaryType");
        primaryTypeProp.setType(metaModelDAO.getPropertyType(PropertyTypeDefinition.NAME));
        primaryTypeProp.setMandatory(true);
        primaryTypeProp.setProtected(true);
        primaryTypeProp.setMultiValued(false);
        M2Property aspectsProp = baseType.createProperty("aspects");
        aspectsProp.setType(metaModelDAO.getPropertyType(PropertyTypeDefinition.NAME));
        aspectsProp.setMandatory(false);
        aspectsProp.setProtected(true);
        aspectsProp.setMultiValued(true);

        // Create Test File Type
        M2Type fileType = metaModelDAO.createType(TEST_TYPE_QNAME_FILE);
        fileType.setSuperClass(baseType);
        fileType.getDefaultAspects().add(referenceAspect);
        M2Property encodingProp = fileType.createProperty("encoding");
        encodingProp.setType(metaModelDAO.getPropertyType(PropertyTypeDefinition.TEXT));
        encodingProp.setMandatory(true);
        encodingProp.setMultiValued(false);
        M2Property mimetypeProp = fileType.createProperty("mimetype");
        mimetypeProp.setType(metaModelDAO.getPropertyType(PropertyTypeDefinition.TEXT));
        mimetypeProp.setMandatory(true);
        mimetypeProp.setMultiValued(false);
        
        // Create Test Folder Type
        M2Type folderType = metaModelDAO.createType(TEST_TYPE_QNAME_FOLDER);
        folderType.setSuperClass(baseType);
        folderType.getDefaultAspects().add(referenceAspect);
        M2ChildAssociation contentsAssoc = folderType.createChildAssociation("contents");
        contentsAssoc.getRequiredToClasses().add(fileType);
        contentsAssoc.setMandatory(false);
        contentsAssoc.setMultiValued(true);
    }
    
    /**
     * Create the model to support the light weight version store implementation     
     */
    private void createVersionModel()
    {
        // Get a reference to the base type and conatiner type
        M2Class baseType = metaModelDAO.getClass(TYPE_QNAME_BASE);
        M2Class containerType = metaModelDAO.getClass(TYPE_QNAME_CONTAINER);
        M2Class referenceType = metaModelDAO.getClass(TYPE_QNAME_REFERENCE);
        
        
        // ===========================================================================
        // Lock Aspect Model Defintions
        
        // Create the lock aspect
        M2Aspect lockAspect = metaModelDAO.createAspect(LockService.ASPECT_QNAME_LOCK);
        
        // Create the lock owner property
        M2Property lockOwnerProperty = lockAspect.createProperty(LockService.PROP_LOCK_OWNER);
        lockOwnerProperty.setType(metaModelDAO.getPropertyType(PropertyTypeDefinition.ANY));
        lockOwnerProperty.setMandatory(false);
        lockOwnerProperty.setMultiValued(false);
        
        
        // ===========================================================================
        // Version Aspect Model Defintions
        
        // Create version aspect
        M2Aspect versionAspect = metaModelDAO.createAspect(VersionService.ASPECT_QNAME_VERSION);
        
        // Create current version label property
        M2Property currentVersionLabelProperty = versionAspect.createProperty(VersionService.PROP_CURRENT_VERSION_LABEL);
        currentVersionLabelProperty.setType(metaModelDAO.getPropertyType(PropertyTypeDefinition.TEXT));
        currentVersionLabelProperty.setMandatory(false); 
        currentVersionLabelProperty.setMultiValued(false);
        
        // TODO there is currently on way to link the aspect to the service implementation
        
        
        // ===========================================================================
        // Light Weight Version Store Model Defintions
        
        // -------------------- versionedAttribute type --------------------
        M2Type versionedAttributeType = metaModelDAO.createType(VersionStoreConst.TYPE_QNAME_VERSIONED_ATTRIBUTE);
        versionedAttributeType.setSuperClass(baseType);
        
        // Create assocQName property
        M2Property assocQNameProperty = versionedAttributeType.createProperty(VersionStoreConst.PROP_QNAME);
        assocQNameProperty.setType(metaModelDAO.getPropertyType(PropertyTypeDefinition.ANY));
        assocQNameProperty.setMandatory(true);
        assocQNameProperty.setMultiValued(false);
        
        // Create value property
        M2Property valueProperty = versionedAttributeType.createProperty(VersionStoreConst.PROP_VALUE);
        valueProperty.setType(metaModelDAO.getPropertyType(PropertyTypeDefinition.ANY));
        valueProperty.setMandatory(true);
        valueProperty.setMultiValued(false);        
        
        // -------------------- versionedChildAssoc type --------------------
        M2Type versionedChildAssocType = metaModelDAO.createType(VersionStoreConst.TYPE_QNAME_VERSIONED_CHILD_ASSOC);
        versionedChildAssocType.setSuperClass(referenceType);
        
        // Create frozen is primary property
        M2Property frozenIsPrimary = versionedChildAssocType.createProperty(
                VersionStoreConst.PROP_IS_PRIMARY);
        frozenIsPrimary.setType(metaModelDAO.getPropertyType(PropertyTypeDefinition.BOOLEAN));
        frozenIsPrimary.setMandatory(true);
        frozenIsPrimary.setMultiValued(false);
        
        // Create qname property
        M2Property qname2 = versionedChildAssocType.createProperty(VersionStoreConst.PROP_ASSOC_QNAME);
        qname2.setType(metaModelDAO.getPropertyType(PropertyTypeDefinition.ANY));
        qname2.setMandatory(true);
        qname2.setMultiValued(false);
                
        // Create frozen nth sibling property
        M2Property frozenNthSibling = versionedChildAssocType.createProperty(
                VersionStoreConst.PROP_NTH_SIBLING);
        frozenNthSibling.setType(metaModelDAO.getPropertyType(PropertyTypeDefinition.INT));
        frozenNthSibling.setMandatory(true);
        frozenNthSibling.setMultiValued(false);
        
        
        // -------------------- version type --------------------
        M2Type versionType = metaModelDAO.createType(VersionStoreConst.TYPE_QNAME_VERSION);
        versionType.setSuperClass(containerType);
        
        // Create verison number property
        M2Property versionNumber = versionType.createProperty(
                Version.PROP_VERSION_NUMBER);
        versionNumber.setType(metaModelDAO.getPropertyType(PropertyTypeDefinition.INT));
        versionNumber.setMandatory(true);
        versionNumber.setMultiValued(false);
        
        // Create verison label property
        M2Property versionLabel = versionType.createProperty(
                Version.PROP_VERSION_LABEL);
        versionLabel.setType(metaModelDAO.getPropertyType(PropertyTypeDefinition.TEXT));  
        versionLabel.setMandatory(true);
        versionLabel.setMultiValued(false);
        
        // Create created date property
        M2Property createdDate = versionType.createProperty(
                Version.PROP_CREATED_DATE);
        createdDate.setType(metaModelDAO.getPropertyType(PropertyTypeDefinition.DATE));  
        createdDate.setMandatory(true);
        createdDate.setMultiValued(false);
        
        // Create frozen node id property
        M2Property frozenNodeId = versionType.createProperty(
                Version.PROP_FROZEN_NODE_ID);
        frozenNodeId.setType(metaModelDAO.getPropertyType(PropertyTypeDefinition.GUID));
        frozenNodeId.setMandatory(true);
        frozenNodeId.setMultiValued(false);
                
        // Create frozen node store id property
        M2Property frozenNodeStoreId = versionType.createProperty(
                Version.PROP_FROZEN_NODE_STORE_ID);
        frozenNodeStoreId.setType(metaModelDAO.getPropertyType(PropertyTypeDefinition.GUID));
        frozenNodeStoreId.setMandatory(true);
        frozenNodeStoreId.setMultiValued(false);
                
        // Create frozen node store protocol property
        M2Property frozenNodeStoreProtocol = versionType.createProperty(
                Version.PROP_FROZEN_NODE_STORE_PROTOCOL);
        frozenNodeStoreProtocol.setType(metaModelDAO.getPropertyType(PropertyTypeDefinition.TEXT));
        frozenNodeStoreProtocol.setMandatory(true);
        frozenNodeStoreProtocol.setMultiValued(false);
                
        // Create frozen node type property        
        M2Property frozenNodeType = versionType.createProperty(
                Version.PROP_FROZEN_NODE_TYPE);
        frozenNodeType.setType(metaModelDAO.getPropertyType(PropertyTypeDefinition.TEXT));
        frozenNodeType.setMandatory(true);
        frozenNodeType.setMultiValued(false);
        
        // Child assoc to versioned attribute details
        M2ChildAssociation versionedAttributesChildAssoc = versionType.createChildAssociation(VersionStoreConst.CHILD_VERSIONED_ATTRIBUTES);
        // TODO the details of this
        
        // Child assoc to versioned child assoc details
        M2ChildAssociation versionedChildAssocsChildAssoc = versionType.createChildAssociation(VersionStoreConst.CHILD_VERSIONED_CHILD_ASSOCS);
        // TODO the details of this
        
        // Add the successor association
        M2Association successorAssoc = versionType.createAssociation(
                VersionStoreConst.ASSOC_SUCCESSOR.getLocalName());
        successorAssoc.getRequiredToClasses().add(versionType);
        successorAssoc.setMandatory(false);
        successorAssoc.setMultiValued(true);
              
        // -------------------- versionHistoryType type --------------------
        M2Type versionHistoryType = metaModelDAO.createType(VersionStoreConst.TYPE_QNAME_VERSION_HISTORY);
        versionHistoryType.setSuperClass(containerType);
        
        // Create versioned node id property
        M2Property nodeIdProperty = versionHistoryType.createProperty(
                VersionStoreConst.PROP_VERSIONED_NODE_ID);
        nodeIdProperty.setType(metaModelDAO.getPropertyType(PropertyTypeDefinition.GUID));
        nodeIdProperty.setMandatory(true);
        nodeIdProperty.setMultiValued(false);
        
        // Add the child assoc
        M2ChildAssociation versionChildAssoc = versionHistoryType.createChildAssociation(
                VersionStoreConst.CHILD_VERSIONS);
        versionChildAssoc.getRequiredToClasses().add(versionType);
        versionChildAssoc.setMandatory(true);
        versionChildAssoc.setMultiValued(true);
        
        // Add the root version association
        M2Association rootVersionAssoc = versionHistoryType.createAssociation(
                VersionStoreConst.ASSOC_ROOT_VERSION.getLocalName());
        rootVersionAssoc.getRequiredToClasses().add(versionType);
        rootVersionAssoc.setMandatory(true);
        rootVersionAssoc.setMultiValued(false);               
    }
}
