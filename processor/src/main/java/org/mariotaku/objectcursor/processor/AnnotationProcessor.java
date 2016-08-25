/*
 *                 Twidere - Twitter client for Android
 *
 *  Copyright (C) 2012-2015 Mariotaku Lee <mariotaku.lee@gmail.com>
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.mariotaku.objectcursor.processor;


import org.mariotaku.library.objectcursor.annotation.*;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class AnnotationProcessor extends AbstractProcessor {


    @Override
    public Set<String> getSupportedAnnotationTypes() {
        HashSet<String> set = new HashSet<>();
        set.add(CursorObject.class.getName());
        set.add(CursorField.class.getName());
        return set;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        HashMap<Name, CursorObjectClassInfo> cursorObjectClasses = new HashMap<>();
        final Elements elements = processingEnv.getElementUtils();
        final Types types = processingEnv.getTypeUtils();
        for (Element element : roundEnv.getElementsAnnotatedWith(CursorObject.class)) {
            final TypeElement type = (TypeElement) element;
            final CursorObjectClassInfo classInfo = new CursorObjectClassInfo(elements, type);
            cursorObjectClasses.put(type.getQualifiedName(), classInfo);
        }
        for (Element element : roundEnv.getElementsAnnotatedWith(BeforeCursorObjectCreated.class)) {
            final TypeElement type = (TypeElement) element.getEnclosingElement();
            final CursorObjectClassInfo classInfo = getOrThrow(cursorObjectClasses, elements, type);
            classInfo.addBeforeCreated(element);
        }
        for (Element element : roundEnv.getElementsAnnotatedWith(AfterCursorObjectCreated.class)) {
            final TypeElement type = (TypeElement) element.getEnclosingElement();
            final CursorObjectClassInfo classInfo = getOrThrow(cursorObjectClasses, elements, type);
            classInfo.addAfterCreated(element);
        }
        for (Element element : roundEnv.getElementsAnnotatedWith(BeforeWriteContentValues.class)) {
            final TypeElement type = (TypeElement) element.getEnclosingElement();
            final CursorObjectClassInfo classInfo = getOrThrow(cursorObjectClasses, elements, type);
            classInfo.addBeforeValueWrite(element);
        }
        for (Element element : roundEnv.getElementsAnnotatedWith(AfterWriteContentValues.class)) {
            final TypeElement type = (TypeElement) element.getEnclosingElement();
            final CursorObjectClassInfo classInfo = getOrThrow(cursorObjectClasses, elements, type);
            classInfo.addAfterValueWrite(element);
        }
        for (Element element : roundEnv.getElementsAnnotatedWith(CursorField.class)) {
            final VariableElement var = (VariableElement) element;
            final TypeElement type = (TypeElement) var.getEnclosingElement();
            final CursorObjectClassInfo classInfo = getOrThrow(cursorObjectClasses, elements, type);
            classInfo.addField(var);
        }

        final Filer filer = processingEnv.getFiler();
        for (CursorObjectClassInfo classInfo : cursorObjectClasses.values()) {
            try {
                if (classInfo.wantCursorIndices) {
                    CursorIndicesClassGenerator cursorIndicesClassGenerator = new CursorIndicesClassGenerator(classInfo, elements);
                    cursorIndicesClassGenerator.saveCursorIndicesFile(filer, elements, types);
                }
                if (classInfo.wantValuesCreator) {
                    ValuesCreatorClassGenerator valuesCreatorClassGenerator = new ValuesCreatorClassGenerator(classInfo, elements);
                    valuesCreatorClassGenerator.saveValuesCreatorFile(filer, elements, types);
                }
                if (classInfo.wantTableInfo) {
                    TableInfoClassGenerator tableInfoClassGenerator = new TableInfoClassGenerator(classInfo, elements);
                    tableInfoClassGenerator.saveValuesCreatorFile(filer, elements, types);
                }
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
        return true;
    }

    private CursorObjectClassInfo getOrThrow(HashMap<Name, CursorObjectClassInfo> cursorObjectClasses, Elements elements,
                                             TypeElement type) {
        final CursorObjectClassInfo classInfo = cursorObjectClasses.get(type.getQualifiedName());
        if (classInfo == null) {
            throw new NullPointerException("Must be annotated with @" + CursorObject.class.getSimpleName());
        }
        return classInfo;
    }
}
