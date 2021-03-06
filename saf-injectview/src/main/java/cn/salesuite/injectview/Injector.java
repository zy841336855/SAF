package cn.salesuite.injectview;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.View;

import java.lang.reflect.Field;

/**
 * Created by Tony Shen on 2016/12/6.
 */

public class Injector {

    public enum Finder {
        DIALOG {
            @Override
            public View findById(Object source, int id) {
                return ((Dialog) source).findViewById(id);
            }
        },
        ACTIVITY {
            @Override
            public View findById(Object source, int id) {
                return ((Activity) source).findViewById(id);
            }

            @Override
            public Object getExtra(Object source, String key, String fieldName) {

                Intent intent = ((Activity) source).getIntent();

                if (intent != null) {
                    Bundle extras = intent.getExtras();

                    Object value = extras != null ? extras.get(key) : null;

                    Field field = null;
                    try {
                        field = source.getClass().getDeclaredField(fieldName);
                    } catch (NoSuchFieldException e) {
                        e.printStackTrace();
                    }

                    if (value==null) {
                        if (field.getType().getName().equals(java.lang.Integer.class.getName())
                                || field.getType().getName().equals("int")) {
                            value = 0;
                        } else if (field.getType().getName().equals(java.lang.Boolean.class.getName())
                                || field.getType().getName().equals("boolean")) {
                            value = false;
                        } else if (field.getType().getName().equals(java.lang.String.class.getName())) {
                            value = "";
                        } else if (field.getType().getName().equals(java.lang.Long.class.getName())
                                || field.getType().getName().equals("long")) {
                            value = 0L;
                        } else if (field.getType().getName().equals(java.lang.Double.class.getName())
                                || field.getType().getName().equals("double")) {
                            value = 0.0;
                        }
                    }

                    if (value != null) {
                        try {
                            field.setAccessible(true);
                            field.set(source,value);
                            return field.get(source);
                        } catch (IllegalAccessException e) {
                            e.printStackTrace();
                        }
                    }
                }

                return null;
            }
        },
        FRAGMENT {
            @Override
            public View findById(Object source, int id) {
                return ((View) source).findViewById(id);
            }
        },
        VIEW {
            @Override
            public View findById(Object source, int id) {
                return ((View) source).findViewById(id);
            }
        },
        VIEW_HOLDER {
            @Override
            public View findById(Object source, int id) {
                return ((View) source).findViewById(id);
            }
        };

        public abstract View findById(Object source, int id);

        public Object getExtra(Object source, String key, String fieldName) {
            return null;
        }
    }

    /**
     * 在Activity中使用注解
     * @param activity
     */
    public static void injectInto(Activity activity){
        inject(activity, activity,Finder.ACTIVITY);
    }

    /**
     * 在fragment中使用注解
     * @param fragment
     * @param v
     * @return
     */
    public static void injectInto(Fragment fragment, View v) {
        inject(fragment,v,Finder.FRAGMENT);
    }

    /**
     * 在adapter中使用注解
     * @param obj
     * @param v
     * @return
     */
    public static void injectInto(Object obj,View v) {
        inject(obj, v,Finder.VIEW_HOLDER);
    }

    private static void inject(Object host, Object source,Finder finder) {
        String className = host.getClass().getName();
        try {
            Class<?> finderClass = Class.forName(className + "$$ViewBinder");
            ViewBinder viewBinder = (ViewBinder) finderClass.newInstance();
            viewBinder.inject(host, source, finder);
        } catch (Exception e) {
            throw new RuntimeException("Unable to inject for " + className, e);
        }
    }
}
