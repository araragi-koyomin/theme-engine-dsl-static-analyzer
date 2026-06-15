# 视图：通用属性

| **标签** | **支持的通用属性** | **不支持的通用属性** |
| --- | --- | --- |
| Arc | x,y,width,height,pivotX,pivotY,rotation,rotationX,rotationY,visibility,align,alignV,enableMove,moveRect,active,category | name,alpha |
| Circle | x,y,width,height,pivotX,pivotY,rotation,rotationX,rotationY,visibility,align,alignV,enableMove,moveRect,active,category | name,alpha |
| DateTime | x,y,pivotX,pivotY,name,rotation,rotationX,rotationY,visibility,align,alignV,active,category | alpha,width,height,enableMove,moveRect |
| Ellipse | x,y,width,height,pivotX,pivotY,rotation,rotationX,rotationY,visibility,align,alignV,enableMove,moveRect,active,category | name,alpha |
| Image | x,y,width,height,pivotX,pivotY,rotation,rotationX,rotationY,visibility,align,alignV,enableMove,moveRect,active,category,name,alpha | / |
| ImageNumber | x,y,pivotX,pivotY,rotation,alpha,visibility,align,alignV,enableMove,moveRect,active,category,name | width,height,rotationX,rotationY |
| ImageSeries | x,y,pivotX,pivotY,rotation,alpha,visibility,align,alignV,enableMove,moveRect,active,category,name | width,height,rotationX,rotationY |
| Line | x,y,pivotX,pivotY,rotation,rotationX,rotationY,visibility,align,alignV,enableMove,moveRect,active,category | name,alpha,width,height |
| Rectangle | x,y,width,height,pivotX,pivotY,rotation,rotationX,rotationY,visibility,align,alignV,enableMove,moveRect,active,category | name,alpha |
| SourceImage | x,y,width,height,name,pivotX,pivotY,rotation,rotationX,rotationY,alpha,visibility,align,alignV,enableMove,moveRect,active | category |
| Text | x,y,name,pivotX,pivotY,rotation,rotationX,rotationY,visibility,align,alignV,enableMove,moveRect,active,alpha,category,width,height | / |
| Time | x,y,name,pivotX,pivotY,rotation,rotationX,rotationY,alpha,visibility,align,alignV,enableMove,moveRect,active,category | width,height |

通用属性为视图组件共有的一系列属性，提供了默认的属性和设置，如果在支持通用属性的视图组件中存在与通用属性相同的属性名，以视图组件的说明为准。通用属性列表如下：

| **参数** | **类型** | **选项** | **注释** |
| --- | --- | --- | --- |
| name | 字符串 | 选填 | 元素变量名，在支持表达式的情况中使用@name的格式来取当前变量的值。 |
| x | 数值 | 选填 | 相对于屏幕左上角的x坐标，单位为像素，显示范围为屏幕的宽（1920*1080的屏幕，显示的宽的范围为0~1080），默认为0，支持表达式。 |
| y | 数值 | 选填 | 相对于屏幕左上角的y坐标，单位为像素，显示范围为屏幕的高（1920*1080的屏幕，显示的高的范围为0~1920），默认为0，支持表达式。 |
| width | 数值 | 选填 | 显示在屏幕上的宽，单位为像素，显示范围为屏幕的宽（1920*1080的屏幕，显示的宽的范围为0~1080），支持表达式，w和width写法都可以。 |
| height | 数值 | 选填 | 显示在屏幕上的高，单位为像素，显示范围为屏幕的高（1920*1080的屏幕，显示的高的范围为0~1920），支持表达式，h和height写法都可以。 |
| pivotX | 数值 | 选填 | 旋转的点的X坐标，相对于View的左上角位置，单位为像素，支持表达式，pivotX和centerX写法都可以。 |
| pivotY | 数值 | 选填 | 旋转的点的Y坐标，相对于View的左上角位置，单位为像素，支持表达式，pivotY和centerY写法都可以。 |
| rotation | 数值 | 选填 | 旋转角度，一周360度，围绕(pivotX,pivotY)点旋转（若无pivotX和pivotY，则默认(0,0)点旋转），支持表达式。正数表示顺时针，负数表示逆时针，rotation和angle写法都可以。 |
| rotationX | 数值 | 选填 | 以X轴为旋转中心旋转，一周360度，支持表达式。正数表示顺时针，负数表示逆时针，rotationX和angleX写法都可以。 |
| rotationY | 数值 | 选填 | 以Y轴为旋转中心旋转，一周360度，支持表达式。正数表示顺时针，负数表示逆时针，rotationY和angleY写法都可以。 |
| alpha | 数值 | 选填 | 透明度，0-255，默认为255，当值小于0时则该值取0，当值大于255时，该值取255，支持表达式。 |
| visibility | 数值 | 选填 | 元素可见性，<=0 不可见 >0可见，true为可见，false为不可见，默认为1，支持表达式。 |
| category | 字符串 | 选填 | 取值"Normal", "Charging", "BatteryLow", "BatteryFull"，表示当手机处于指定充电状态时显示该元素。 |
| align | 字符串 | 选填 | 坐标点水平对齐方式left, center, right，对齐的效果为view的左上角x坐标分别表示该view的左、中、右位置。 |
| alignV | 字符串 | 选填 | 坐标点垂直对齐方式top, center, bottom，对齐的效果为view的左上角y坐标分别表示该view的上、中、下位置。 |
| enableMove | 字符串 | 选填 | 元素是否支持移动，当值为false或者0时表示不可移动，当值为true或者非0值表示可移动，默认为0。 |
| moveRect | 字符串 | 选填 | 移动区域，当enableMove为可移动时有效。格式为moveRect="minH,minV,maxH,maxV"，分别表示：最小水平移动，最小垂直移动，最大水平移动，最大垂直移动。四个参数类型为数值，单位为像素，不支持表达式。例如：移动目标的原坐标为(x,y)，则该目标水平方向的移动范围是：(x+minH)~(x+maxH)，垂直方向的移动范围是：(y+minV)~(y+maxV)。 |
| active | 数值 | 选填 | 激活状态，0代表不激活，视图不显示，所有的动画和可见性均失效，默认值1。 |
