package org.mariotaku.objectcursor.processor;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeName;
import org.mariotaku.library.objectcursor.annotation.CursorField;
import org.mariotaku.library.objectcursor.annotation.CursorObject;
import org.mariotaku.library.objectcursor.converter.EmptyCursorFieldConverter;

import javax.lang.model.element.*;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import java.util.*;

/**
 * Created by mariotaku on 15/11/27.
 */
public class CursorObjectClassInfo {

    static final ClassName EMPTY_CONVERTER = ClassName.get(EmptyCursorFieldConverter.class);
    static final ClassName STRING = ClassName.get(String.class);

    final TypeElement objectType;
    final ClassName objectClassName;

    final List<CursorFieldInfo> fieldInfoList;
    final Map<String, ClassName> converterMaps;
    final Set<TypeName> customTypes;
    final boolean wantCursorIndices;
    final boolean wantValuesCreator;
    final boolean wantTableInfo;

    final Set<Element> beforeCreated, afterCreated;

    public CursorObjectClassInfo(Elements elements, TypeElement objectType) {
        this.objectType = objectType;
        objectClassName = ClassName.get(objectType);
        final CursorObject annotation = objectType.getAnnotation(CursorObject.class);
        wantCursorIndices = annotation.cursorIndices();
        wantValuesCreator = annotation.valuesCreator();
        wantTableInfo = annotation.tableInfo();
        fieldInfoList = new ArrayList<>();
        customTypes = new HashSet<>();
        converterMaps = new HashMap<>();
        beforeCreated = new HashSet<>();
        afterCreated = new HashSet<>();
    }

    public static ClassName getSuffixedClassName(Elements elements, TypeElement cls, String suffix) {
        final String packageName = String.valueOf(elements.getPackageOf(cls).getQualifiedName());
        final String binaryName = String.valueOf(elements.getBinaryName(cls));
        return ClassName.get(packageName, binaryName.substring(packageName.length() + 1) + suffix);
    }

    public ClassName getConverter(String name) {
        return converterMaps.get(name);
    }

    public CursorFieldInfo addField(VariableElement field) {
        if (field.getKind() != ElementKind.FIELD) throw new AssertionError();
        final Set<Modifier> modifiers = field.getModifiers();
        if (modifiers.contains(Modifier.STATIC)) {
            throw modifierNotAllowed(Modifier.STATIC, field);
        }
        if (modifiers.contains(Modifier.PRIVATE)) {
            throw modifierNotAllowed(Modifier.PRIVATE, field);
        }
        if (modifiers.contains(Modifier.PROTECTED)) {
            throw modifierNotAllowed(Modifier.PROTECTED, field);
        }
        final CursorFieldInfo fieldInfo = new CursorFieldInfo(field);

        ClassName converterName;
        try {
            converterName = (ClassName) TypeName.get(fieldInfo.annotation.converter());
        } catch (MirroredTypeException mte) {
            converterName = (ClassName) TypeName.get(mte.getTypeMirror());
        }

        if (!EMPTY_CONVERTER.equals(converterName)) {
            // Custom data type
            converterMaps.put(fieldInfo.objectFieldName, converterName);
            customTypes.add(fieldInfo.type);
        }
        fieldInfoList.add(fieldInfo);
        return fieldInfo;
    }

    private IllegalArgumentException modifierNotAllowed(Modifier modifier, VariableElement field) {
        throw new IllegalArgumentException(modifier + " field is not allowed for " + objectClassName + "." + field);
    }

    public TypeMirror getSuperclass() {
        return objectType.getSuperclass();
    }

    public String getPackageName() {
        return objectClassName.packageName();
    }

    public void addBeforeCreated(Element element) {
        beforeCreated.add(element);
    }

    public void addAfterCreated(Element element) {
        afterCreated.add(element);
    }

    public Set<ClassName> customConverters() {
        return new HashSet<>(converterMaps.values());
    }

    public Set<TypeName> customTypes() {
        return customTypes;
    }

    public Set<Element> beforeCreated() {
        return beforeCreated;
    }

    public Set<Element> afterCreated() {
        return afterCreated;
    }

    public static class CursorFieldInfo {

        final String objectFieldName;
        final String indexFieldName;
        final String columnName;
        final CursorField annotation;

        final TypeName type;

        public CursorFieldInfo(VariableElement field) {
            type = TypeName.get(field.asType());
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
