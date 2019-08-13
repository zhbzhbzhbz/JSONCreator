# 简介

 不依赖任何第三方库，包括org.json的，Json 生成类

# 调用方法

 `new JsonCreatorWithoutLib().toJSONString(Object object,boolean printNull)`

# 功能

- 支持基本类型、数组、List、Map、自定义Bean的多层嵌套；
- 支持设置是否忽略null；
- 支持对特殊字符如"\的解析
- 支持多线程
 
# 速度：类似FastJson，比Gson快

| JSON长度 | JSONCreator耗时 | Gson耗时 | FastJSON耗时 | 
| --- | --- | --- | --- | 
| **157KB** | 111ms | 151ms | 未测 | 
| **12KB** | 7ms | 18ms | 未测 | 
| **2.3KB** | 0ms | 3ms | 2ms | 
| **39B** | 0ms | 9ms | 0ms |
  
# 测试

使用实际业务生成的多层嵌套的Bean，以及特殊情况的测试用例测试，与Gson的结果完全相同
 
注：这个库初衷是公司的SDK不允许用第三方库，就自己模仿org.json的JSONObject的逻辑手写了一个，目前运行良好
