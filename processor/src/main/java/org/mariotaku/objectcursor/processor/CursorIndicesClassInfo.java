package org.mariotaku.objectcursor.processor;

import android.database.Cursor;
import com.squareup.javapoet.*;
import org.mariotaku.library.objectcursor.ObjectCursor;
import org.mariotaku.library.objectcursor.annotation.CursorField;
import org.mariotaku.library.objectcursor.converter.EmptyCursorFieldConverter;
import org.mariotaku.library.objectcursor.internal.ParameterizedTypeImpl;

import javax.lang.model.element.*;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.util.*;

/**
 * Created by mariotaku on 15/11/27.
 */
public class CursorIndicesClassInfo {
    static final ClassName STRING = ClassName.get(String.class);
    static final ClassName EMPTY_CONVERTER = ClassName.get(EmptyCursorFieldConverter.class);

    private final ObjectClassInfo objectClass;
    private final List<FieldInfo> fieldInfoList;
    private final Map<String, ClassName> converterMaps;
    private final Set<TypeName> customTypes;
    private final TypeElement type;
    private Set<Element> beforeCreated, afterCreated;

    public CursorIndicesClassInfo(Elements elements, TypeElement type) {
        this.type = type;
        this.objectClass = getObjectClass(elements, type);
        fieldInfoList = new ArrayList<>();
        customTypes = new HashSet<>();
        converterMaps = new HashMap<>();
        beforeCreated = new HashSet<>();
        afterCreated = new HashSet<>();
    }

    @Override
    public String toString() {
        return "CursorIndicesClassInfo{" +
                "objectClass=" + objectClass +
                ", fieldInfoList=" + fieldInfoList +
                '}';
    }

    public FieldInfo addField(VariableElement field) {
        if (field.getKind() != ElementKind.FIELD)
            throw new IllegalArgumentException("@" + CursorField.class.getSimpleName() + " is only applicable for fields");
        final Set<Modifier> modifiers = field.getModifiers();
        if (modifiers.contains(Modifier.STATIC)) {
            throw new IllegalArgumentException("static field is not allowed");
        }
        if (modifiers.contains(Modifier.PRIVATE)) {
            throw new IllegalArgumentException("private field is not allowed");
        }
        if (modifiers.contains(Modifier.PROTECTED)) {
            throw new IllegalArgumentException("protected field is not allowed");
        }
        final FieldInfo fieldInfo = new FieldInfo(field);

        ClassName name;
        try {
            name = (ClassName) TypeName.get(fieldInfo.annotation.converter());
        } catch (MirroredTypeException mte) {
            name = (ClassName) TypeName.get(mte.getTypeMirror());
        }

        if (!EMPTY_CONVERTER.equals(name)) {
            // Custom data type
            converterMaps.put(fieldInfo.objectFieldName, name);
            customTypes.add(ClassName.get(fieldInfo.type));
        }
        fieldInfoList.add(fieldInfo);
        return fieldInfo;
    }

    public static ObjectClassInfo getObjectClass(String packageName, String binaryName) {
        return new ObjectClassInfo(packageName, binaryName);
    }

    public static ObjectClassInfo getObjectClass(Elements elements, TypeElement type) {
        final String packageName = String.valueOf(elements.getPackageOf(type).getQualifiedName());
        final String binaryName = String.valueOf(elements.getBinaryName(type));
        return getObjectClass(packageName, binaryName);
    }

    public CharSequence getCursorIndexQualifiedName() {
        return objectClass.getCursorIndexQualifiedNameString();
    }

    public void writeCursorIndexClassFile(Appendable appendable, Types types) throws IOException {
        final TypeSpec.Builder builder = TypeSpec.classBuilder(objectClass.getCursorIndexClassNameWithoutPackageNameString());
        TypeElement superClass = (TypeElement) types.asElement(type.getSuperclass());

        builder.addSuperinterface(ParameterizedTypeName.get(ClassName.get(ObjectCursor.CursorIndices.class), ClassName.get(type)));
        builder.addModifiers(Modifier.PUBLIC, Modifier.FINAL);

        builder.addFields(getConverterFields());

        builder.addFields(getTypeFields());

        builder.addFields(getIndexFields());

        // Add constructor
        builder.addMethod(getConstructor());

        builder.addMethod(createNewObjectMethod());

        builder.addMethod(createFromCursorMethod());

        JavaFile.builder(objectClass.packageName, builder.build()).build().writeTo(appendable);
    }


    private List<FieldSpec> getConverterFields() {
        List<FieldSpec> fieldSpecs = new ArrayList<>();
        for (ClassName converterClass : new HashSet<>(converterMaps.values())) {
            fieldSpecs.add(FieldSpec.builder(converterClass, getConverterFieldName(converterClass), Modifier.FINAL, Modifier.STATIC)
                    .initializer("new $T()", converterClass)
                    .build());
        }
        return fieldSpecs;
    }

    private List<FieldSpec> getTypeFields() {
        List<FieldSpec> fieldSpecs = new ArrayList<>();
        for (TypeName typeName : customTypes) {
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

    private static String getConverterFieldName(TypeName converterClass) {
        return converterClass.toString().replaceAll("[^\\w\\d]", "_").toUpperCase(Locale.US);
    }


    private List<FieldSpec> getIndexFields() {
        List<FieldSpec> fieldSpecs = new ArrayList<>();
        for (FieldInfo fieldInfo : fieldInfoList) {
            fieldSpecs.add(FieldSpec.builder(TypeName.INT, fieldInfo.indexFieldName, Modifier.PUBLIC)
                    .initializer("-1")
                    .build());
        }
        return fieldSpecs;
    }


    private MethodSpec getConstructor() {
        final MethodSpec.Builder builder = MethodSpec.constructorBuilder();
        builder.addModifiers(Modifier.PUBLIC);
        builder.addParameter(Cursor.class, "cursor");

        builder.beginControlFlow("for (int i = 0, j = cursor.getColumnCount(); i < j; i++)");

        for (FieldInfo fieldInfo : fieldInfoList) {
            builder.addStatement("$L = cursor.getColumnIndex($S)", fieldInfo.indexFieldName, fieldInfo.columnName);
        }

        builder.endControlFlow();
        return builder.build();
    }


    private MethodSpec createNewObjectMethod() {
        final MethodSpec.Builder builder = MethodSpec.methodBuilder("newObject");
        builder.addAnnotation(Override.class);
        builder.addModifiers(Modifier.PUBLIC);
        builder.addParameter(Cursor.class, "cursor");
        builder.returns(ClassName.get(type));

        builder.addStatement("$T instance = new $T()", type, type);

        if (beforeCreated != null) {
            for (Element element : beforeCreated) {
                builder.addStatement("instance.$L()", element.getSimpleName());
            }
        }

        for (FieldInfo fieldInfo : fieldInfoList) {
            builder.beginControlFlow("if ($L != -1)", fieldInfo.indexFieldName);
            addSetValueStatement(builder, fieldInfo);
            builder.endControlFlow();
        }


        if (afterCreated != null) {
            for (Element element : afterCreated) {
                builder.addStatement("instance.$L()", element.getSimpleName());
            }
        }

        builder.addStatement("return instance");
        return builder.build();
    }


    private MethodSpec createFromCursorMethod() {
        final MethodSpec.Builder builder = MethodSpec.methodBuilder("fromCursor");
        builder.addModifiers(Modifier.PUBLIC, Modifier.STATIC);
        builder.addParameter(Cursor.class, "cursor");
        builder.returns(ClassName.get(type));

        final ClassName indicesClassName = objectClass.getCursorIndexClassName();
        builder.addStatement("$T indices = new $T(cursor)", indicesClassName, indicesClassName);
        builder.addStatement("return indices.newObject(cursor)");
        return builder.build();
    }

    private void addSetValueStatement(MethodSpec.Builder builder, FieldInfo fieldInfo) {
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
            final ClassName converterClass = converterMaps.get(fieldInfo.objectFieldName);
            if (converterClass == null) {

            } else {
                builder.addStatement("instance.$L = ($T) $L.parseField(cursor, $L, $L)", fieldInfo.objectFieldName,
                        fieldType, getConverterFieldName(converterClass), fieldInfo.indexFieldName,
                        getConverterFieldName(fieldType));
            }
        }
    }

    public void addBeforeCreated(Element element) {
        beforeCreated.add(element);
    }

    public void addAfterCreated(Element element) {
        afterCreated.add(element);
    }

    static class ObjectClassInfo {
        String packageName;
        String binaryNameWithoutPackage;

        public ObjectClassInfo(String packageName, String binaryName) {
            this.packageName = packageName;
            this.binaryNameWithoutPackage = binaryName.substring(packageName.length() + 1);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ObjectClassInfo that = (ObjectClassInfo) o;

            if (!packageName.equals(that.packageName)) return false;
            return binaryNameWithoutPackage.equals(that.binaryNameWithoutPackage);

        }

        @Override
        public int hashCode() {
            int result = packageName.hashCode();
            result = 31 * result + binaryNameWithoutPackage.hashCode();
            return result;
        }

        @Override

        public String toString() {
            return "ObjectClassInfo{" +
                    "packageName='" + packageName + '\'' +
                    ", binaryNameWithoutPackage='" + binaryNameWithoutPackage + '\'' +
                    '}';
        }

        public String getObjectClassNameString() {
            return packageName + "." + binaryNameWithoutPackage;
        }

        public String getCursorIndexQualifiedNameString() {
            return packageName + "." + getCursorIndexClassNameWithoutPackageNameString();
        }

        public String getCursorIndexClassNameWithoutPackageNameString() {
            return binaryNameWithoutPackage + "CursorIndices";
        }

        public ClassName getCursorIndexClassName() {
            return ClassName.get(packageName, getCursorIndexClassNameWithoutPackageNameString());
        }
    }

    static class FieldInfo {

        private final String objectFieldName;
        private final String indexFieldName;
        private final String columnName;
        private final CursorField annotation;

        private final TypeMirror type;

        public FieldInfo(VariableElement field) {
            type = field.asType();
            annotation = field.getAnnotation(CursorField.class);
            columnName = annotation.value();
            objectFieldName = String.valueOf(field.getSimpleName());
            if (annotation.indexFieldName().length() > 0) {
                indexFieldName = annotation.indexFieldName().replace('.', '_');
            } else {
                indexFieldName = objectFieldName;
            }
        }

        @Override
        public String toString() {
            return "FieldInfo{" +
                    "type=" + type +
                    ", columnName='" + columnName + '\'' +
                    '}';
        }

    }
}
