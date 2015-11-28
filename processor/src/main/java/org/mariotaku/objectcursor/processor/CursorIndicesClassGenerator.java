package org.mariotaku.objectcursor.processor;

import android.database.Cursor;
import com.squareup.javapoet.*;
import org.mariotaku.library.objectcursor.ObjectCursor;
import org.mariotaku.library.objectcursor.annotation.CursorObject;
import org.mariotaku.library.objectcursor.internal.ParameterizedTypeImpl;

import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Created by mariotaku on 15/11/28.
 */
public class CursorIndicesClassGenerator {
    public static final String CURSOR_INDICES_SUFFIX = "CursorIndices";

    static final ClassName STRING = ClassName.get(String.class);
    private final CursorObjectClassInfo objectClassInfo;
    private final ClassName indicesClassName;
    private final String indicesClassNameWithoutPackage;

    CursorIndicesClassGenerator(CursorObjectClassInfo objectClassInfo, Elements elements) {
        this.objectClassInfo = objectClassInfo;
        final String packageName = String.valueOf(elements.getPackageOf(objectClassInfo.objectType).getQualifiedName());
        final String binaryName = String.valueOf(elements.getBinaryName(objectClassInfo.objectType));
        indicesClassNameWithoutPackage = binaryName.substring(packageName.length() + 1) + CURSOR_INDICES_SUFFIX;
        indicesClassName = ClassName.get(packageName, indicesClassNameWithoutPackage);
    }

    private static String getConverterFieldName(TypeName converterClass) {
        return converterClass.toString().replaceAll("[^\\w\\d]", "_").toUpperCase(Locale.US);
    }

    void writeContent(Appendable appendable, Elements elements, Types types) throws IOException {
        final TypeSpec.Builder builder = TypeSpec.classBuilder(indicesClassNameWithoutPackage);
        TypeElement superClass = (TypeElement) types.asElement(objectClassInfo.getSuperclass());

        ClassName parentIndicesClass = null;
        // Super class has CursorIndex implementation
        if (superClass.getAnnotation(CursorObject.class) != null) {
            parentIndicesClass = CursorObjectClassInfo.getSuffixedClassName(elements, superClass, CURSOR_INDICES_SUFFIX);
        }

        builder.addSuperinterface(ParameterizedTypeName.get(ClassName.get(ObjectCursor.CursorIndices.class),
                objectClassInfo.objectClassName));
        builder.addModifiers(Modifier.PUBLIC);

        builder.addFields(getConverterFields());

        builder.addFields(getTypeFields());


        if (parentIndicesClass != null) {
            builder.addField(parentIndicesClass, "parentIndices", Modifier.FINAL);
        }

        builder.addFields(getIndexFields());

        // Add constructor
        builder.addMethod(getConstructor(parentIndicesClass));

        builder.addMethod(createNewObjectMethod());

        builder.addMethod(createBeforeCreatedMethod(parentIndicesClass));

        builder.addMethod(createAfterCreatedMethod(parentIndicesClass));

        builder.addMethod(createParseFieldsMethod(parentIndicesClass));

        builder.addMethod(createFromCursorMethod());

        JavaFile.builder(objectClassInfo.getPackageName(), builder.build()).build().writeTo(appendable);
    }

    private List<FieldSpec> getConverterFields() {
        List<FieldSpec> fieldSpecs = new ArrayList<>();
        for (ClassName converterClass : objectClassInfo.customConverters()) {
            fieldSpecs.add(FieldSpec.builder(converterClass, getConverterFieldName(converterClass), Modifier.FINAL, Modifier.STATIC)
                    .initializer("new $T()", converterClass)
                    .build());
        }
        return fieldSpecs;
    }

    private List<FieldSpec> getTypeFields() {
        List<FieldSpec> fieldSpecs = new ArrayList<>();
        for (TypeName typeName : objectClassInfo.customTypes()) {
            // String field is not a custom type
            if (STRING.equals(typeName)) continue;
            final FieldSpec.Builder builder = FieldSpec.builder(ParameterizedType.class,
                    getConverterFieldName(typeName), Modifier.FINAL, Modifier.STATIC);
            if (typeName instanceof ParameterizedTypeName) {
                ParameterizedTypeName parameterizedTypeName = (ParameterizedTypeName) typeName;
                Object[] formatArgs = new Object[2 + parameterizedTypeName.typeArguments.size()];
                final StringBuilder placeHolders = new StringBuilder();
                formatArgs[0] = ParameterizedTypeImpl.class;
                formatArgs[1] = parameterizedTypeName.rawType;
                for (int i = 0, j = parameterizedTypeName.typeArguments.size(); i < j; i++) {
                    placeHolders.append(", $T.class");
                    formatArgs[i + 2] = parameterizedTypeName.typeArguments.get(i);
                }
                final String format = String.format(Locale.ROOT, "$T.get($T.class, null%s)", placeHolders);
                builder.initializer(format, formatArgs);
            } else {
                builder.initializer("$T.get($T.class, null)", ParameterizedTypeImpl.class, typeName);
            }
            fieldSpecs.add(builder.build());
        }
        return fieldSpecs;
    }

    private List<FieldSpec> getIndexFields() {
        List<FieldSpec> fieldSpecs = new ArrayList<>();
        for (CursorObjectClassInfo.CursorFieldInfo fieldInfo : objectClassInfo.fieldInfoList) {
            fieldSpecs.add(FieldSpec.builder(TypeName.INT, fieldInfo.indexFieldName, Modifier.PUBLIC)
                    .initializer("-1")
                    .build());
        }
        return fieldSpecs;
    }


    private MethodSpec getConstructor(ClassName parentIndexClass) {
        final MethodSpec.Builder builder = MethodSpec.constructorBuilder();
        builder.addModifiers(Modifier.PUBLIC);
        builder.addParameter(Cursor.class, "cursor");
        if (parentIndexClass != null) {
            builder.addStatement("parentIndices = new $T(cursor)", parentIndexClass);
        }

        for (CursorObjectClassInfo.CursorFieldInfo fieldInfo : objectClassInfo.fieldInfoList) {
            builder.addStatement("$L = cursor.getColumnIndex($S)", fieldInfo.indexFieldName, fieldInfo.columnName);
        }

        return builder.build();
    }


    private MethodSpec createNewObjectMethod() {
        final MethodSpec.Builder builder = MethodSpec.methodBuilder("newObject");
        builder.addAnnotation(Override.class);
        builder.addModifiers(Modifier.PUBLIC);
        builder.addParameter(Cursor.class, "cursor");
        ClassName objectType = ClassName.get(objectClassInfo.objectType);
        builder.returns(objectClassInfo.objectClassName);

        builder.addStatement("$T instance = new $T()", objectClassInfo.objectClassName, objectClassInfo.objectClassName);
        builder.addStatement("callBeforeCreated(instance)");
        builder.addStatement("parseFields(instance, cursor)");
        builder.addStatement("callAfterCreated(instance)");
        builder.addStatement("return instance");
        return builder.build();
    }

    private MethodSpec createBeforeCreatedMethod(ClassName parentIndicesClass) {
        final MethodSpec.Builder builder = MethodSpec.methodBuilder("callBeforeCreated");
        builder.addAnnotation(Override.class);
        builder.addModifiers(Modifier.PUBLIC);
        builder.addParameter(objectClassInfo.objectClassName, "instance");

        if (parentIndicesClass != null) {
            builder.addStatement("parentIndices.callBeforeCreated(instance)");
        }

        for (Element element : objectClassInfo.beforeCreated()) {
            builder.addStatement("instance.$L()", element.getSimpleName());
        }

        return builder.build();
    }

    private MethodSpec createAfterCreatedMethod(ClassName parentIndicesClass) {
        final MethodSpec.Builder builder = MethodSpec.methodBuilder("callAfterCreated");
        builder.addAnnotation(Override.class);
        builder.addModifiers(Modifier.PUBLIC);
        builder.addParameter(objectClassInfo.objectClassName, "instance");

        for (Element element : objectClassInfo.afterCreated()) {
            builder.addStatement("instance.$L()", element.getSimpleName());
        }

        if (parentIndicesClass != null) {
            builder.addStatement("parentIndices.callAfterCreated(instance)");
        }

        return builder.build();
    }

    private MethodSpec createParseFieldsMethod(ClassName parentIndicesClass) {
        final MethodSpec.Builder builder = MethodSpec.methodBuilder("parseFields");
        builder.addAnnotation(Override.class);
        builder.addModifiers(Modifier.PUBLIC);
        builder.addParameter(objectClassInfo.objectClassName, "instance");
        builder.addParameter(Cursor.class, "cursor");

        if (parentIndicesClass != null) {
            builder.addStatement("parentIndices.parseFields(instance, cursor)");
        }

        for (CursorObjectClassInfo.CursorFieldInfo fieldInfo : objectClassInfo.fieldInfoList) {
            builder.beginControlFlow("if ($L != -1)", fieldInfo.indexFieldName);
            addSetValueStatement(builder, fieldInfo);
            builder.endControlFlow();
        }

        return builder.build();
    }

    private MethodSpec createFromCursorMethod() {
        final MethodSpec.Builder builder = MethodSpec.methodBuilder("fromCursor");
        builder.addModifiers(Modifier.PUBLIC, Modifier.STATIC);
        builder.addParameter(Cursor.class, "cursor");
        builder.returns(objectClassInfo.objectClassName);

        builder.addStatement("$T indices = new $T(cursor)", indicesClassName, indicesClassName);
        builder.addStatement("return indices.newObject(cursor)");
        return builder.build();
    }

    private void addSetValueStatement(MethodSpec.Builder builder, CursorObjectClassInfo.CursorFieldInfo fieldInfo) {
        TypeName fieldType = ClassName.get(fieldInfo.type);
        try {
            fieldType = fieldType.unbox();
        } catch (UnsupportedOperationException e) {
            // Ignore
        }
        if (fieldType == TypeName.BOOLEAN) {
            builder.addStatement("instance.$L = cursor.getShort($L) == 1", fieldInfo.objectFieldName, fieldInfo.indexFieldName);
        } else if (fieldType == TypeName.INT) {
            builder.addStatement("instance.$L = cursor.getInt($L)", fieldInfo.objectFieldName, fieldInfo.indexFieldName);
        } else if (fieldType == TypeName.LONG) {
            builder.addStatement("instance.$L = cursor.getLong($L)", fieldInfo.objectFieldName, fieldInfo.indexFieldName);
        } else if (fieldType == TypeName.FLOAT) {
            builder.addStatement("instance.$L = cursor.getFloat($L)", fieldInfo.objectFieldName, fieldInfo.indexFieldName);
        } else if (fieldType == TypeName.DOUBLE) {
            builder.addStatement("instance.$L = cursor.getDouble($L)", fieldInfo.objectFieldName, fieldInfo.indexFieldName);
        } else if (fieldType == TypeName.SHORT) {
            builder.addStatement("instance.$L = cursor.getShort($L)", fieldInfo.objectFieldName, fieldInfo.indexFieldName);
        } else if (fieldType.equals(STRING)) {
            builder.addStatement("instance.$L = cursor.getString($L)", fieldInfo.objectFieldName, fieldInfo.indexFieldName);
        } else {
            final ClassName converterClass = objectClassInfo.getConverter(fieldInfo.objectFieldName);
            if (converterClass == null) {

            } else {
                builder.addStatement("instance.$L = ($T) $L.parseField(cursor, $L, $L)", fieldInfo.objectFieldName,
                        fieldType, getConverterFieldName(converterClass), fieldInfo.indexFieldName,
                        getConverterFieldName(fieldType));
            }
        }
    }


    public void saveCursorIndicesFile(Filer filer, Elements elements, Types types) throws IOException {
        JavaFileObject fileObj = filer.createSourceFile(indicesClassName.toString());
        try (Writer writer = fileObj.openWriter()) {
            writeContent(writer, elements, types);
            writer.flush();
        }
    }
}
