package cn.salesuite.injectview.complier;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import java.util.ArrayList;
import java.util.List;

import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;

import cn.salesuite.base.TypeUtils;

/**
 * Created by Tony Shen on 2016/12/7.
 */

public class AnnotatedClass {

    public TypeElement mClassElement;
    public List<BindViewField> mFields;
    public List<BindViewFields> mFieldss;
    public List<ExtraField> mExtraFields;
    public List<OnClickMethod> mMethods;
    public Elements mElementUtils;

    public AnnotatedClass(TypeElement classElement, Elements elementUtils) {
        this.mClassElement = classElement;
        this.mElementUtils = elementUtils;
        this.mFields = new ArrayList<>();
        this.mFieldss = new ArrayList<>();
        this.mExtraFields = new ArrayList<>();
        this.mMethods = new ArrayList<>();
    }

    public String getFullClassName() {
        return mClassElement.getQualifiedName().toString();
    }

    public void addField(BindViewField field) {
        mFields.add(field);
    }

    public void addFields(BindViewFields field) {
        mFieldss.add(field);
    }

    public void addExtraField(ExtraField extraField) {
        mExtraFields.add(extraField);
    }

    public void addMethod(OnClickMethod method) {
        mMethods.add(method);
    }

    public JavaFile generateFinder() {

        // method inject(final T host, Object source, FINDER finder)
        MethodSpec.Builder injectMethodBuilder = MethodSpec.methodBuilder("inject")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .addParameter(TypeName.get(mClassElement.asType()), "host", Modifier.FINAL)
                .addParameter(TypeName.OBJECT, "source")
                .addParameter(TypeUtils.FINDER, "finder");

        for (BindViewField field : mFields) {
            // find view
            injectMethodBuilder.addStatement("host.$N = ($T)(finder.findById(source, $L))", field.getFieldName(),
                    ClassName.get(field.getFieldType()), field.getResId());
        }

        String fieldTypeName = null;
        for (BindViewFields field:mFieldss) {
            // find views
            fieldTypeName = field.getFieldType().toString();

            if (fieldTypeName.startsWith("java.util.List")) {
                int[] ids = field.getResIds();

                injectMethodBuilder.addStatement("host.$N = new $T<>()", field.getFieldName(), TypeUtils.ARRAY_LIST);

                int first = fieldTypeName.indexOf("<");
                if (first == -1) continue;

                int last = fieldTypeName.lastIndexOf(">");
                if (last == -1) continue;

                String clazz = fieldTypeName.substring(first+1,last);
                int dot = clazz.lastIndexOf(".");
                if (dot == -1) continue;

                String packageName = clazz.substring(0,dot);
                String simpleName = clazz.substring(dot+1);
                int length = ids.length;
                ClassName className = ClassName.get(packageName,simpleName);
                for (int i=0;i<length;i++) {
                    injectMethodBuilder.addStatement("host.$N.add(($T)(finder.findById(source, $L)))",field.getFieldName(), className,ids[i]);
                }
            } else if (fieldTypeName.endsWith("[]")){
                int[] ids = field.getResIds();
                int length = ids.length;
                int first = fieldTypeName.indexOf("[");
                if (first == -1) continue;

                String clazz = fieldTypeName.substring(0,first);

                int dot = clazz.lastIndexOf(".");
                if (dot == -1) continue;

                String packageName = clazz.substring(0,dot);
                String simpleName = clazz.substring(dot+1);
                ClassName className = ClassName.get(packageName,simpleName);
                injectMethodBuilder.addStatement("host.$N = new $T[$L]", field.getFieldName(),className,length);

                for (int i =0;i<length;i++) {
                    injectMethodBuilder.addStatement("host.$N[$L] = ($T)(finder.findById(source, $L))",field.getFieldName(), i, className,ids[i]);
                }
            }

        }

        for (ExtraField field : mExtraFields) {
            // find extra
            injectMethodBuilder.addStatement("host.$N = ($T)(finder.getExtra(source, $L, $L))", field.getFieldName(),
                    ClassName.get(field.getFieldType()), "\""+field.getKey()+"\"","\""+field.getFieldName()+"\"");
        }

        if (mMethods.size() > 0) {
            injectMethodBuilder.addStatement("$T listener", TypeUtils.ANDROID_ON_CLICK_LISTENER);

            for (OnClickMethod method : mMethods) {
                // declare OnClickListener anonymous class
                TypeSpec listener = TypeSpec.anonymousClassBuilder("")
                        .addSuperinterface(TypeUtils.ANDROID_ON_CLICK_LISTENER)
                        .addMethod(MethodSpec.methodBuilder("onClick")
                                .addAnnotation(Override.class)
                                .addModifiers(Modifier.PUBLIC)
                                .returns(TypeName.VOID)
                                .addParameter(TypeUtils.ANDROID_VIEW, "view")
                                .addStatement("host.$N()", method.getMethodName())
                                .build())
                        .build();
                injectMethodBuilder.addStatement("listener = $L ", listener);
                for (int id : method.ids) {
                    // set listeners
                    injectMethodBuilder.addStatement("finder.findById(source, $L).setOnClickListener(listener)", id);
                }
            }
        }

        // generate whole class
        TypeSpec finderClass = TypeSpec.classBuilder(mClassElement.getSimpleName() + "$$ViewBinder")
                .addModifiers(Modifier.PUBLIC)
                .addSuperinterface(ParameterizedTypeName.get(TypeUtils.VIEW_BINDER, TypeName.get(mClassElement.asType())))
                .addMethod(injectMethodBuilder.build())
                .build();

        String packageName = mElementUtils.getPackageOf(mClassElement).getQualifiedName().toString();

        return JavaFile.builder(packageName, finderClass).build();
    }
}
