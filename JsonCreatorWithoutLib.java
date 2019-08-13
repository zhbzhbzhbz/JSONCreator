package com.xindun.sdk.ias.utils;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;

/**
 * 【不依赖Org.JSON的·比GSON快的·JSON生成库】
 * Author：zhouhongbo199102@163.com
 * 用法：new JsonCreatorWithoutLib().toJSONString(Object object,boolean printNull)
 * 功能：
 * -支持基本类型、数组、List、Map、自定义Bean的多层嵌套；
 * -支持设置是否忽略null；
 * -支持对特殊字符如"\的解析
 * -支持多线程
 * 速度对比（类似FastJson，比Gson快）：157KB的JSON，111ms VS 151ms（GSON）；12KB的JSON，7ms VS 18ms（Gson）；2.3KB的JSON，0ms VS 3ms(Gson) VS 2ms(FastJson)；39B，0ms VS 9ms(Gson) VS 0ms(FastJson)
 * 测试：使用实际业务生成的多层嵌套的Bean，以及特殊情况的测试用例测试，与Gson的结果完全相同
 * 初衷：不能依赖第三方库、Android API19才有JSONArray，所以就全部重写
 * 注：1. 不支持注解 2.对于[1,null,1]，GSON任何情况都会显示null
 */

public class JsonCreatorWithoutLib {
    private static final Object NULL = new Object() {
        @Override
        public boolean equals(Object o) {
            return o == this || o == null; // API specifies this broken equals implementation
        }

        @Override
        public String toString() {
            return "null";
        }
    };
    private static HashSet<Class> BasicType = new HashSet<Class>() {{
        add(Integer.class);
        add(Float.class);
        add(Short.class);
        add(Double.class);
        add(Boolean.class);
        add(String.class);
        add(Byte.class);
        add(Long.class);
        add(Character.class);
    }};
    private static HashSet<String> ExcludeTypeName = new HashSet<String>() {{
        add("serialVersionUID");
    }};
    //加快反射速度
    private Map<Class, Field[]> fieldsMap = new HashMap<>();
    private StringBuilder out = new StringBuilder();

    public String toJSONString(Object obj, boolean printNull) {
        if (obj == null) {
            return null;
        }
        traverse(obj, printNull);
        return out.toString();
    }

    private void traverse(Object obj, boolean printNull) {
        //如果为空，必须放在第一个
        if (obj == null) {
            append(NULL); //因为JSONObject已经在下面处理过了，而JSONArray里的null是必须显示的，所以此处不做判断
        }
        //如果是基本类型(或者可以用field.getType().isPrimitive()判断)，就直接添加到JsonObject中
        else if (BasicType.contains(obj.getClass())) {
            basicTypeToString(obj);
        }
        // 如果是Array、List、Set，遍历然后递归
        else if (obj.getClass().isArray()) { //用Collection.class.isAssignableFrom(obj.getClass())也可以
            traverseArray(obj, printNull);
        } else if (obj instanceof Collection) {
            traverseCollection(obj, printNull);
        }
        //如果是Map，就递归转为JSONObject
        else if (obj instanceof Map) { //或者用Collection.class.isAssignableFrom(obj.getClass())
            traverseMap(obj, printNull);
        }
        //如果是Bean
        else {
            traverseBean(obj, printNull);
        }
    }

    private void traverseArray(Object obj, boolean printNull) {
        append("["); //起始
        int length = Array.getLength(obj);
        for (int i = 0; i < length; i++) {
            if (i != 0)
                append(","); //中间的逗号
            traverse(Array.get(obj, i), printNull);
        }
        append("]"); //结束
    }

    private void traverseCollection(Object obj, boolean printNull) {
        append("["); //起始
        boolean isStart = true;
        for (Object tempObj : (Collection) obj) {
            if (!isStart)
                append(","); //中间的逗号
            else
                isStart = false;
            traverse(tempObj, printNull);
        }
        append("]"); //结束
    }

    private void traverseBean(Object obj, boolean printNull) {
        try {
            boolean isObjectStart = true;
            append("{"); //起始
            Field[] fields = getObjectFields(obj);
            for (Field field : fields) {
                String fieldName = field.getName();
                Object memberObj = field.get(obj);
                //如果是this$0，$changeBean，serialVersionUID，就不算在内 (后两个是instant run相关的)
                //（需要最先判断，否则会出现$change:null）
                if (field.isSynthetic() || ExcludeTypeName.contains(fieldName)) {
                    continue;
                }
                //如果是null并且不打印null就跳过
                else if (memberObj == null && !printNull) {
                    continue;
                }
                //此处如果是null并且打印null就继续
                else {
                    //这里不能放在for的开始，否则如果有continue的情况就会多一个逗号
                    if (!isObjectStart)
                        append(","); //中间的逗号
                    else
                        isObjectStart = false;
                    stringFormat(checkName(fieldName)); //检查是否为Null
                    append(":");
                    traverse(memberObj, printNull);
                }
            }
            append("}"); //结束
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void traverseMap(Object obj, boolean printNull) {
        try {
            boolean isObjectStart = true;
            append("{"); //起始
            for (Map.Entry entry : ((Map<Object, Object>) obj).entrySet()) {
                String fieldName = entry.getKey().toString();
                Object memberObj = entry.getValue();
                //如果是null并且不打印null就跳过
                if (memberObj == null && !printNull) {
                    continue;
                }
                //此处如果是null并且打印null就继续
                else {
                    //这里不能放在for的开始，否则如果有continue的情况就会多一个逗号
                    if (!isObjectStart)
                        append(","); //中间的逗号
                    else
                        isObjectStart = false;
                    stringFormat(checkName(fieldName)); //检查是否为Null
                    append(":");
                    traverse(memberObj, printNull);
                }
            }
            append("}"); //结束
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //通过反射获取对象的Field
    private Field[] getObjectFields(Object obj) {
        Field[] fields;
        Class clazz = obj.getClass();
        //如果Map里没有Fields
        if (!fieldsMap.containsKey(clazz)) {
            fields = traverseFields(clazz);
            for (Field field : fields) {
                field.setAccessible(true);
            }
            fieldsMap.put(clazz, fields);
        } else {
            fields = fieldsMap.get(clazz);
        }
        return fields;
    }

    //getDeclaredFields只能获取到当前Bean（不含父类）所有的成员变量（包括private），因此递归去获取父类的
    //注：亲测Gson也是这个递归顺序
    private Field[] traverseFields(Class clazz) {
        LinkedList<Field> fieldList = new LinkedList<>();
        while (clazz != Object.class) {
            Collections.addAll(fieldList, clazz.getDeclaredFields());
            clazz = clazz.getSuperclass();
        }
        return fieldList.toArray(new Field[1]);
    }

    //根据JSON规则，对不同类型的对象进行不同处理
    private void basicTypeToString(Object obj) {
        if (obj == null
                || obj instanceof Boolean
                || obj == NULL
                || obj instanceof Number) { //long int short float byte double 都是Number的子类
            append(obj);
        }
        //Character、String
        else if (obj instanceof Character || obj instanceof String) {
            stringFormat(obj.toString());
        } else {
            throw new RuntimeException("Unsupported Field Type");
        }
    }

    private void stringFormat(String value) {
        append("\"");
        for (int i = 0, length = value.length(); i < length; i++) {
            char c = value.charAt(i);
            // From RFC 4627, "All Unicode characters may be placed within the quotation marks except for the characters that must be escaped: quotation mark, reverse solidus, and the control characters (U+0000 through U+001F)."
            switch (c) {
                case '"':
                case '\\':
                case '/':
                    append('\\');
                    append(c);
                    break;
                case '\t':
                    append("\\t");
                    break;
                case '\b':
                    append("\\b");
                    break;
                case '\n':
                    append("\\n");
                    break;
                case '\r':
                    append("\\r");
                    break;
                case '\f':
                    append("\\f");
                    break;
                default:
                    if (c <= 0x1F) {
                        append(String.format("\\u%04x", (int) c));
                    } else {
                        append(c);
                    }
                    break;
            }
        }
        append("\"");
    }

    private String checkName(String name) throws Exception {
        if (name == null) {
            throw new Exception("Names must be non-null");
        }
        return name;
    }

    private void append(Object str) {
        out.append(str);
    }
}
