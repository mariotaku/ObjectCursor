package org.mariotaku.objectcursor.processor;

import android.database.Cursor;

import com.squareup.javapoet.ArrayTypeName;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import org.mariotaku.library.objectcursor.ObjectCursor;
import org.mariotaku.library.objectcursor.annotation.CursorField;
import org.mariotaku.library.objectcursor.annotation.CursorObject;
import org.mariotaku.library.objectcursor.internal.ParameterizedTypeImpl;

import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.JavaFileObject;

import static org.mariotaku.library.objectcursor.ObjectCursor.CursorIndices.CURSOR_INDICES_SUFFIX;

/**
 * Created by mariotaku on 15/11/28.
 */
public class CursorIndicesClassGenerator {

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

        builder.addSuperinterface(ParameterizedTypeName.get(ClassName.get(ObjectCursor.CursorIndices.class),
                objectClassInfo.objectClassName));
        builder.addModifiers(Modifier.PUBLIC);

        builder.addFields(getConverterFields());

        builder.addFields(getTypeFields());

        TypeElement superClass = (TypeElement) types.asElement(objectClassInfo.getSuperclass());

        ClassName parentIndicesClass = null;
        List<String> superFields = null;
        // Super class has CursorIndex implementation
        if (superClass.getAnnotation(CursorObject.class) != null) {
            parentIndicesClass = CursorObjectClassInfo.getSuffixedClassName(elements, superClass, CURSOR_INDICES_SUFFIX);

            builder.addField(parentIndicesClass, "parentIndices", Modifier.FINAL);

            superFields = new ArrayList<>();

            for (VariableElement superField : ElementFilter.fieldsIn(superClass.getEnclosedElements())) {
                if (superField.getAnnotation(CursorField.class) != null) {
                    superFields.add(String.valueOf(superField.getSimpleName()));
                }
            }

        }


        builder.addFields(getIndexFields(superFields));

        // Add constructor
        builder.addMethod(getConstructor(parentIndicesClass, superFields));

        builder.addMethod(createNewObjectMethod());

        builder.addMethod(createBeforeCreatedMethod(parentIndicesClass));

        builder.addMethod(createAfterCreatedMethod(parentIndicesClass));

        builder.addMethod(createParseFieldsMethod(parentIndicesClass));

        builder.addMethod(createGetIndexMethod());

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
            if (CursorObjectClassInfo.STRING.equals(typeName)) continue;
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

    private List<FieldSpec> getIndexFields(List<String> superFields) {
        List<FieldSpec> fieldSpecs = new ArrayList<>();
        if (superFields != null)
            for (String name : superFields) {
                fieldSpecs.add(FieldSpec.builder(TypeName.INT, name, Modifier.PUBLIC)
                        .initializer("-1")
                        .build());
            }

        for (CursorObjectClassInfo.CursorFieldInfo fieldInfo : objectClassInfo.fieldInfoList) {
            if (fieldInfo.columnName.isEmpty()) continue;
            fieldSpecs.add(FieldSpec.builder(TypeName.INT, fieldInfo.indexFieldName, Modifier.PUBLIC)
                    .initializer("-1")
                    .build());
        }

        return fieldSpecs;
    }


    private MethodSpec getConstructor(ClassName parentIndexClass, List<String> superFields) {
        final MethodSpec.Builder builder = MethodSpec.constructorBuilder();
        builder.addModifiers(Modifier.PUBLIC);
        builder.addParameter(Cursor.class, "cursor");
        if (parentIndexClass != null) {
            builder.addStatement("parentIndices = new $T(cursor)", parentIndexClass);
        }
        if (superFields != null) {
            for (String superField : superFields) {
                builder.addStatement("this.$L = parentIndices.$L", superField, superField);
            }
        }
        for (CursorObjectClassInfo.CursorFieldInfo fieldInfo : objectClassInfo.fieldInfoList) {
            if (fieldInfo.columnName.isEmpty()) continue;
            builder.addStatement("this.$L = cursor.getColumnIndex($S)", fieldInfo.indexFieldName, fieldInfo.columnName);
        }

        return builder.build();
    }


    private MethodSpec createNewObjectMethod() {
        final MethodSpec.Builder builder = MethodSpec.methodBuilder("newObject");
        builder.addAnnotation(Override.class);
        builder.addModifiers(Modifier.PUBLIC);
        builder.addParameter(Cursor.class, "cursor");
        builder.addException(IOException.class);
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
        builder.addException(IOException.class);

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
        builder.addException(IOException.class);

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
        builder.addException(IOException.class);

        if (parentIndicesClass != null) {
            builder.addStatement("parentIndices.parseFields(instance, cursor)");
        }

        for (CursorObjectClassInfo.CursorFieldInfo fieldInfo : objectClassInfo.fieldInfoList) {
            if (fieldInfo.columnName.isEmpty()) {
                addSetValueStatement(builder, fieldInfo);
            } else {
                builder.beginControlFlow("if ($L != -1)", fieldInfo.indexFieldName);
                addSetValueStatement(builder, fieldInfo);
                builder.endControlFlow();
            }
        }

        return builder.build();
    }

    private MethodSpec createGetIndexMethod() {
        final MethodSpec.Builder builder = MethodSpec.methodBuilder("get");
        builder.addAnnotation(Override.class);
        builder.addModifiers(Modifier.PUBLIC);
        builder.addParameter(String.class, "columnName");
        builder.returns(int.class);
        builder.addCode("switch (columnName) {");
        for (final CursorObjectClassInfo.CursorFieldInfo fieldInfo : objectClassInfo.fieldInfoList) {
            builder.addCode("  case $S: return $L;", fieldInfo.columnName, fieldInfo.indexFieldName);
        }
        builder.addCode("}");
        builder.addStatement("return -1");
        return builder.build();
    }

    private void addSetValueStatement(MethodSpec.Builder builder, CursorObjectClassInfo.CursorFieldInfo fieldInfo)
            throws UnsupportedFieldTypeException {
        boolean supported = true;
        TypeName fieldType = fieldInfo.type;
        try {
            fieldType = fieldType.unbox();
        } catch (UnsupportedOperationException e) {
            // Ignore
        }
        if (fieldInfo.useSetter()) {
            builder.addCode("instance.$L(", fieldInfo.objectFieldSetter);
        } else {
            builder.addCode("instance.$L = ", fieldInfo.objectFieldName);
        }
        if (fieldType == TypeName.BOOLEAN) {
            builder.addCode("cursor.getShort($L) == 1", fieldInfo.indexFieldName);
        } else if (fieldType == TypeName.INT) {
            builder.addCode("cursor.getInt($L)", fieldInfo.indexFieldName);
        } else if (fieldType == TypeName.LONG) {
            builder.addCode("cursor.getLong($L)", fieldInfo.indexFieldName);
        } else if (fieldType == TypeName.FLOAT) {
            builder.addCode("cursor.getFloat($L)", fieldInfo.indexFieldName);
        } else if (fieldType == TypeName.DOUBLE) {
            builder.addCode("cursor.getDouble($L)", fieldInfo.indexFieldName);
        } else if (fieldType == TypeName.SHORT) {
            builder.addCode("cursor.getShort($L)", fieldInfo.indexFieldName);
        } else if (fieldType.equals(CursorObjectClassInfo.STRING)) {
            builder.addCode("cursor.getString($L)", fieldInfo.indexFieldName);
        } else {
            final ClassName converterClass = objectClassInfo.getConverter(fieldInfo.objectFieldName);
            if (converterClass != null) {
                if (!fieldInfo.columnName.isEmpty()) {
                    builder.addCode("($T) $L.parseField(cursor, $L, $L)", fieldType,
                            getConverterFieldName(converterClass), fieldInfo.indexFieldName,
                            getConverterFieldName(fieldType));
                } else {
                    builder.addCode("($T) $L.parseField(cursor, $L, $L)", fieldType,
                            getConverterFieldName(converterClass), -1, getConverterFieldName(fieldType));
                }
            } else if (fieldType instanceof ArrayTypeName) {
                if (((ArrayTypeName) fieldType).componentType == TypeName.BYTE) {
                    builder.addCode("cursor.getBlob($L)", fieldInfo.indexFieldName);
                } else {
                    supported = false;
                }
            } else {
                supported = false;
            }
        }
        if (fieldInfo.useSetter()) {
            builder.addCode(")");
        }
        builder.addStatement("");
        if (!supported) {
            throw new UnsupportedFieldTypeException(String.format("Unsupported type %s in %s.%s", fieldInfo.type,
                    objectClassInfo.objectClassName, fieldInfo.objectFieldName));
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
