package cn.salesuite.injectview.complier;

import com.google.auto.service.AutoService;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;

import cn.salesuite.base.Utils;
import cn.salesuite.injectview.annotations.InjectExtra;
import cn.salesuite.injectview.annotations.InjectView;
import cn.salesuite.injectview.annotations.InjectViews;
import cn.salesuite.injectview.annotations.OnClick;

/**
 * 使用 Google 的 auto-service 库可以自动生成 META-INF/services/javax.annotation.processing.Processor 文件
 * Created by Tony Shen on 2016/12/6.
 */
@AutoService(Processor.class)
public class InjectViewProcessor extends AbstractProcessor {

    private Filer mFiler; //文件相关的辅助类
    private Elements mElementUtils; //元素相关的辅助类
    private Messager mMessager; //日志相关的辅助类

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        mFiler = processingEnv.getFiler();
        mElementUtils = processingEnv.getElementUtils();
        mMessager = processingEnv.getMessager();
    }

    /**
     * @return 指定哪些注解应该被注解处理器注册
     */
    @Override
    public Set<String> getSupportedAnnotationTypes() {
        Set<String> types = new LinkedHashSet<>();
        types.add(InjectView.class.getCanonicalName());
        types.add(InjectViews.class.getCanonicalName());
        types.add(OnClick.class.getCanonicalName());
        types.add(InjectExtra.class.getCanonicalName());
        return types;
    }

    /**
     * @return 指定使用的 Java 版本。通常返回 SourceVersion.latestSupported()。
     */
    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    private Map<String, AnnotatedClass> mAnnotatedClassMap = new HashMap<>();

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        mAnnotatedClassMap.clear();

        try {
            processInjectView(roundEnv);
            processInjectViews(roundEnv);
            processInjectExtra(roundEnv);
            processOnClick(roundEnv);
        } catch (IllegalArgumentException e) {
            Utils.error(mMessager,e.getMessage());
            return true; // stop process
        }

        for (AnnotatedClass annotatedClass : mAnnotatedClassMap.values()) {
            try {
                Utils.info(mMessager,"Generating file for %s", annotatedClass.getFullClassName());
                annotatedClass.generateFinder().writeTo(mFiler);
            } catch (IOException e) {
                Utils.error(mMessager,"Generate file failed, reason: %s", e.getMessage());
                return true;
            }
        }
        return true;
    }

    private void processInjectView(RoundEnvironment roundEnv) throws IllegalArgumentException {

        AnnotatedClass annotatedClass = null;
        BindViewField field = null;
        for (Element element : roundEnv.getElementsAnnotatedWith(InjectView.class)) {
            annotatedClass = getAnnotatedClass(element,"@InjectView");
            if (annotatedClass==null)
                continue;

            field = new BindViewField(element);
            annotatedClass.addField(field);
        }
    }

    private void processInjectViews(RoundEnvironment roundEnv) {
        AnnotatedClass annotatedClass = null;
        BindViewFields field = null;
        for (Element element : roundEnv.getElementsAnnotatedWith(InjectViews.class)) {
            annotatedClass = getAnnotatedClass(element,"@InjectViews");
            if (annotatedClass==null)
                continue;

            field = new BindViewFields(element);
            annotatedClass.addFields(field);
        }
    }

    private void processInjectExtra(RoundEnvironment roundEnv) {
        Set<Element> set = (Set<Element>) roundEnv.getElementsAnnotatedWith(InjectExtra.class);
        if (set != null && set.size()>0) {
            AnnotatedClass annotatedClass = null;
            ExtraField field = null;
            for (Element element:set) {
                annotatedClass = getAnnotatedClass(element,"@InjectExtra");
                if (annotatedClass==null)
                    continue;

                field = new ExtraField(element);
                annotatedClass.addExtraField(field);
            }
        }
    }

    private void processOnClick(RoundEnvironment roundEnv) {
        for (Element element : roundEnv.getElementsAnnotatedWith(OnClick.class)) {
            AnnotatedClass annotatedClass = getAnnotatedClass(element,"@OnClick");
            if (annotatedClass==null)
                continue;

            OnClickMethod method = new OnClickMethod(element);
            annotatedClass.addMethod(method);
        }
    }

    private AnnotatedClass getAnnotatedClass(Element element,String annotationName) {
        TypeElement classElement = (TypeElement) element.getEnclosingElement();

        //检测是否是支持的注解类型，如果不是里面会报错
        if (!Utils.isValidClass(mMessager,classElement,annotationName)) {
            return null;
        }

        String fullClassName = classElement.getQualifiedName().toString();
        AnnotatedClass annotatedClass = mAnnotatedClassMap.get(fullClassName);
        if (annotatedClass == null) {
            annotatedClass = new AnnotatedClass(classElement, mElementUtils);
            mAnnotatedClassMap.put(fullClassName, annotatedClass);
        }
        return annotatedClass;
    }
}
