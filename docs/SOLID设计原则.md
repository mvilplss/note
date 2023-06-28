---
title: SOLID设计原则：提高代码质量的指导原则
date: 2023-06-21
categories:
- 方法论
tags:
- 设计原则
copyright: true
cover: https://www.freecodecamp.org/news/content/images/size/w2000/2023/02/start-graph--1-.png
---
# 背景
近期在开发过程中碰到了一个庞然大物：
- 成员变量：95个
- 方法：165个
- 使用IF条件语句：1071个
- 代码行数：10272行
- 半年内commit提交包含修改该类次数：200次

就是这个庞然大物让我觉得有必要和大家一起回忆一下面向对象的五个原则SOLID；
# 什么是SOLID设计原则？
SOLID设计原则是一组软件设计原则，用于指导面向对象编程中的良好设计实践。这些原则旨在提高代码的可维护性、可扩展性和重用性，以便于软件系统的开发、维护和演化。

<image src="https://www.freecodecamp.org/news/content/images/size/w2000/2023/02/start-graph--1-.png" ></image>
1. 单一职责原则（Single Responsibility Principle，SRP）
2. 开放封闭原则（Open-Closed Principle，OCP）
3. 里氏替换原则（Liskov Substitution Principle，LSP）
4. 接口隔离原则（Interface Segregation Principle，ISP）
5. 依赖倒置原则（Dependency Inversion Principle，DIP）

# SOLID详细介绍
在本节中将向您展示 SOLID 缩写词的每个部分的含义，通过正反面代码说明每个原则的使用方法。

## 单一职责原则（Single Responsibility Principle，SRP）
> 单一职责原则（Single Responsibility Principle，SRP）是软件设计中的一个原则，它指出一个类或模块应该只有一个责任或关注点。
> 
SRP 强调一个类或模块应该只负责完成一个单一的任务或职责。这意味着一个类应该只有一个引起它变化的原因。如果一个类具有多个职责，那么当其中一个职责发生变化时，可能会影响到其他职责，导致代码的理解、测试和维护变得困难。

SRP 的目标是提高代码的内聚性（Cohesion）。内聚性表示一个类或模块中各个元素之间的相关性和一致性。高内聚的类或模块具有清晰的目标和职责，各个元素之间紧密相关，彼此协同工作。相反，低内聚的类或模块具有多种职责，元素之间关联性较弱，代码变得难以理解和维护。

通过遵循 SRP，可以获得以下优点：

- 提高可维护性：每个类或模块只关注一个职责，当需要修改或扩展某个职责时，只需修改该类或模块，而不会影响其他部分的代码。

- 提高可测试性：职责单一的类或模块更容易进行单元测试，因为测试代码只需关注该职责，并且不会受到其他职责的干扰。

- 提高代码的可读性和理解性：一个类或模块只负责一个职责，代码的功能和逻辑更加清晰明确，易于理解和阅读。

- 降低耦合度：当每个类或模块只负责一个职责时，它们之间的依赖关系更清晰，耦合度更低，减少了代码之间的相互影响和依赖。

### 反例代码
```
public class UserService {
    public void createUser(String username, String email) {
        // 创建用户的逻辑
    }
    
    public void sendEmail(String email, String message) {
        // 发送电子邮件的逻辑
    }
    
    public void generateReport(List<User> users) {
        // 生成报表的逻辑
    }
    
    public void compressFile(String filePath) {
        // 压缩文件的逻辑
    }
    
    public void encryptFile(String filePath) {
        // 加密文件的逻辑
    }
}
```
在上述代码中，UserService 类违反了单一职责原则，承担了多个职责。它包含了创建用户、发送电子邮件、生成报表、压缩文件和加密文件等功能。这样的设计导致了以下问题：

- 代码的可维护性下降：当一个类负责多个职责时，每次修改其中一个职责可能会影响其他职责的代码。这增加了代码的复杂性和维护成本。

- 代码的可测试性下降：由于多个职责的交织在一起，单元测试变得困难。测试一个特定职责的方法时，必须考虑其他职责的依赖和副作用。
### 建议代码
```
public class UserService {
    public void createUser(String username, String email) {
        // 创建用户的逻辑
    }
}

public class EmailService {
    public void sendEmail(String email, String message) {
        // 发送电子邮件的逻辑
    }
}

public class ReportService {
    public void generateReport(List<User> users) {
        // 生成报表的逻辑
    }
}

public class FileCompressor {
    public void compressFile(String filePath) {
        // 压缩文件的逻辑
    }
}

public class FileEncryptor {
    public void encryptFile(String filePath) {
        // 加密文件的逻辑
    }
}

```
通过将原来的 UserService 类拆分成独立的类，每个类负责一个单一的职责，代码变得更加清晰、可维护和可扩展。每个类都遵循单一职责原则，并且职责之间的耦合性降低。这样的设计提高了代码的可测试性和可理解性，减少了代码修改时的风险和影响范围。

需要注意的是，SRP 并不是要求每个类或模块只包含一个方法或属性，而是指明一个类或模块的整体功能应该是高度相关的，共同完成一个单一的职责。在实践中，需要根据具体情况来判断职责的划分方式，避免划分得过于细小或过于笼统。

## 开放封闭原则（Open-Closed Principle，OCP）
> 开放封闭原则（Open-Closed Principle，OCP）是软件设计中的一个原则，它指出软件实体（类、模块、函数等）应该对扩展开放，对修改关闭。

具体来说，OCP 要求设计的实体应该能够通过扩展来增加新的功能，而无需修改已有的代码。这意味着在引入新的需求或变化时，应该通过添加新的代码、类或模块来实现，而不是直接修改现有的代码。通过遵循 OCP，可以有效地减少代码的修改，从而降低引入错误和破坏现有功能的风险。

开放封闭原则的核心思想是通过抽象和多态来实现可扩展性。通过定义适当的抽象接口、基类或接口，并通过多态性来实现具体实现的替换，可以使系统更加灵活和可扩展。

遵循开放封闭原则的优点包括：

- 可维护性：由于不需要修改现有的代码，所以引入新功能或变化时，只需要编写新的代码，保持现有代码的稳定性。这简化了维护过程并降低了错误引入的风险。

- 可扩展性：通过扩展已有的抽象接口或基类，并添加新的实现类，可以方便地引入新的功能，而无需修改现有的代码。这提高了系统的可扩展性，可以灵活地适应未来的需求变化。

- 可复用性：通过定义抽象接口或基类，可以实现代码的高度复用。新的实现类可以基于相同的接口或基类构建，从而利用已有的代码和逻辑，减少重复开发。

- 可测试性：由于对现有代码的修改较少，新添加的功能可以更容易地进行单元测试，而无需重新测试整个系统。

### 反例代码
```
public class PaymentProcessor {
    public void processPayment(Payment payment) {
        if (payment.getMethod() == PaymentMethod.CREDIT_CARD) {
            // 处理信用卡支付的逻辑
        } else if (payment.getMethod() == PaymentMethod.PAYPAL) {
            // 处理PayPal支付的逻辑
        } else if (payment.getMethod() == PaymentMethod.WECHAT_PAY) {
            // 处理微信支付的逻辑
        }
        // 更多支付方式的处理逻辑...
    }
}
```
在上述代码中，PaymentProcessor 类违反了开放封闭原则。当需要引入新的支付方式时，需要修改 processPayment 方法的代码来添加新的支付方式的处理逻辑。这样的设计存在以下问题：

- 违反了开放封闭原则：添加新的支付方式要求修改已有的代码，而不是通过扩展来实现新功能。这增加了代码的脆弱性和维护成本。

- 代码的可扩展性差：每次需要添加新的支付方式时，都需要修改原有代码，这可能导致引入错误和破坏现有功能的风险。
### 建议代码
```
public interface PaymentMethod {
    void processPayment(Payment payment);
}

public class CreditCardPayment implements PaymentMethod {
    public void processPayment(Payment payment) {
        // 处理信用卡支付的逻辑
    }
}

public class PayPalPayment implements PaymentMethod {
    public void processPayment(Payment payment) {
        // 处理PayPal支付的逻辑
    }
}

public class WeChatPayment implements PaymentMethod {
    public void processPayment(Payment payment) {
        // 处理微信支付的逻辑
    }
}

public class PaymentProcessor {
    private List<PaymentMethod> paymentMethods;
    
    public PaymentProcessor(List<PaymentMethod> paymentMethods) {
        this.paymentMethods = paymentMethods;
    }
    
    public void processPayment(Payment payment) {
        for (PaymentMethod method : paymentMethods) {
            if (method.supports(payment.getMethod())) {
                method.processPayment(payment);
                break;
            }
        }
    }
}

```
通过上述改进，我们将支付方式的处理逻辑抽象为接口 PaymentMethod，并为每个支付方式创建一个具体的实现类。在 PaymentProcessor 类中，我们将支持的支付方式存储在一个列表中，并使用循环遍历的方式找到对应的支付方式来处理支付。这样，当需要添加新的支付方式时，只需要创建新的支付方式实现类并添加到 paymentMethods 列表中，而不需要修改现有的代码。这样的设计遵循了开放封闭原则，使得系统更加可扩展和易维护。

需要注意的是，完全做到完全遵循开放封闭原则可能并非总是可行的。有时候，修改已有的代码可能是必要的，尤其是在面对较大的架构变化或重构时。然而，遵循开放封闭原则可以作为一个指导原则，在设计阶段考虑如何通过扩展来实现新功能，以最大程度地减少对现有代码的修改。
## 里氏替换原则（Liskov Substitution Principle，LSP）
> 里氏替换原则（Liskov Substitution Principle，LSP）是面向对象设计中的一个原则，它是由计算机科学家Barbara Liskov在1987年提出的。LSP指出，如果S是T的子类型，那么在任何程序中，只要使用T类型的对象，都可以用S类型的对象替换而不影响程序的正确性。

更简单地说，LSP要求子类型必须能够替代其父类型，而不会引发错误、破坏预期行为或违反系统的契约。这意味着子类在继承父类时应保持接口规范的一致性，并遵循父类的行为约定。

遵循LSP的关键要点包括：

- 子类型必须实现父类型的所有约定：子类型应该实现父类型所定义的所有方法和属性，包括参数类型、返回类型和异常规范。这确保了在使用父类型对象的地方，可以安全地替换为子类型对象。

- 子类型可以通过扩展增加功能，但不能减少父类型的行为：子类型可以添加额外的方法、属性或行为，但不能修改或删除父类型的方法或属性。这样可以保证调用方对父类型的假设和期望不会受到破坏。

- 子类型的前置条件（输入约束）不能比父类型更强：子类型的方法应该接受比父类型更宽松的前置条件，即更弱的约束。这确保了子类型对象可以在不违反父类型契约的情况下替换父类型对象。

- 子类型的后置条件（输出约束）不能比父类型更弱：子类型的方法应该提供比父类型更强的后置条件，即更严格的约束。这确保了子类型对象的行为至少与父类型对象一致，并可以进一步加强约束。



通过遵循LSP，可以实现良好的代码复用、模块化和可扩展性。通过合理地定义父类和子类之间的关系，代码可以更灵活地适应变化，并且可以通过多态性实现代码的高度抽象和可扩展性。

### 反例代码
```
public class Rectangle {
    protected int width;
    protected int height;
    
    public void setWidth(int width) {
        this.width = width;
    }
    
    public void setHeight(int height) {
        this.height = height;
    }
    
    public int calculateArea() {
        return width * height;
    }
}

public class Square extends Rectangle {
    @Override
    public void setWidth(int width) {
        this.width = width;
        this.height = width;
    }
    
    @Override
    public void setHeight(int height) {
        this.width = height;
        this.height = height;
    }
}

```
在上述代码中，Rectangle 是一个矩形类，Square 是一个正方形类，它继承自 Rectangle。看起来似乎正方形是矩形的特例，但实际上这种继承关系违反了里氏替换原则。原因如下：

- Square 类重写了父类的 setWidth 和 setHeight 方法，将宽度和高度都设置为相同的值。这违反了矩形的定义，因为矩形的宽度和高度可以独立设置，而正方形的宽度和高度应该始终相等。

- Rectangle 类的 calculateArea 方法计算矩形的面积，假设宽度和高度是不同的。但是在 Square 类中，由于强制相等的设置逻辑，当调用 calculateArea 方法时，会得到错误的结果。

这个设计问题的根源在于 Square 类与 Rectangle 类之间的行为差异。虽然正方形可以被视为特殊类型的矩形，但是它们的行为并不完全一致，因为正方形的宽度和高度是紧密耦合的。

### 建议代码
```
public abstract class Shape {
    public abstract int calculateArea();
}

public class Rectangle extends Shape {
    protected int width;
    protected int height;
    
    public void setWidth(int width) {
        this.width = width;
    }
    
    public void setHeight(int height) {
        this.height = height;
    }
    
    @Override
    public int calculateArea() {
        return width * height;
    }
}

public class Square extends Shape {
    private int side;
    
    public void setSide(int side) {
        this.side = side;
    }
    
    @Override
    public int calculateArea() {
        return side * side;
    }
}
```
在上述改进的代码中，我们引入了一个抽象的 Shape 类作为父类，Rectangle 和 Square 都继承自 Shape。这样，宽度和高度的设置变成了矩形特有的行为，而正方形只需要设置边长即可。每个子类都实现了自己的 calculateArea 方法，以确保正确计算各自形状的面积。

通过这样的改进，我们遵循了里氏替换原则，子类对象可以替换父类对象而不会破坏系统的正确性。每个子类都遵循了父类的行为契约，同时也保留了自己特有的行为。这样，代码更加清晰、可扩展和易于维护。

## 接口隔离原则（Interface Segregation Principle，ISP）
> 接口隔离原则（Interface Segregation Principle，ISP）是软件设计中的一个原则，它指出客户端不应该依赖它不需要的接口。换句话说，一个类或模块不应该强迫它的客户端依赖于那些它们不使用的接口。

ISP的核心思想是将庞大臃肿的接口拆分成更小、更具体的接口，以便客户端只需依赖于它们真正需要的接口。这样可以实现以下目标：

- 减少接口的冗余：通过拆分接口，可以消除那些对于某些客户端而言不相关或无用的方法。这样可以减少接口的冗余，使接口更加清晰、精简和可读。

- 避免接口污染：当一个接口的功能过于庞大时，它可能包含许多与不同领域或模块相关的方法。这会导致接口的污染，使得客户端需要实现或调用与其业务无关的方法，增加了理解和维护的困难。

- 提高灵活性和可扩展性：通过将接口拆分为更小的、更专注的部分，可以实现更高的灵活性和可扩展性。客户端只需依赖于它们真正需要的接口，当需要添加新功能时，只需实现相关的接口，而无需影响其他部分。

- 降低耦合度：接口的拆分可以减少类或模块之间的依赖关系，降低耦合度。这样，当一个接口发生变化时，只会影响到与该接口直接相关的类或模块，而不会波及其他不相关的部分。
### 反例代码
```
public interface Printer {
    void print();
    void scan();
    void fax();
}
```
在上述代码中，Printer 接口定义了打印、扫描和传真的方法。然而，不是每个设备都具备这三个功能，因此这个接口违反了接口隔离原则。原因如下：

- 接口冗余：对于某些设备而言，可能只需要实现打印功能，但它们仍然被要求实现扫描和传真的方法。这导致了接口的冗余，使得实现类需要实现与其功能无关的方法。

- 接口污染：将不相关的方法放在同一个接口中，使得接口的定义变得模糊，难以理解和使用。客户端在使用接口时可能会困惑于哪些方法是必须实现的，哪些方法是可选的。

### 建议代码
```
public interface Printer {
    void print();
}

public interface Scanner {
    void scan();
}

public interface Fax {
    void fax();
}

public class SimplePrinter implements Printer {
    public void print() {
        // 执行打印操作
    }
}

public class SimpleScanner implements Scanner {
    public void scan() {
        // 执行扫描操作
    }
}

public class MultiFunctionDevice implements Printer, Scanner, Fax {
    public void print() {
        // 执行打印操作
    }
    
    public void scan() {
        // 执行扫描操作
    }
    
    public void fax() {
        // 执行传真操作
    }
}
```
在上述改进的代码中，我们将原始的 Printer 接口拆分为三个接口：Printer、Scanner 和 Fax。每个接口只包含单一的方法，以适应不同设备的功能需求。这样，实现类只需实现自己需要的接口，而不需要实现不相关的方法。

同时，我们还提供了针对不同设备的具体实现类：SimplePrinter（只支持打印功能）、SimpleScanner（只支持扫描功能）和 MultiFunctionDevice（支持多功能：打印、扫描和传真）。

通过这样的设计，我们遵循了接口隔离原则，每个类或模块只需依赖于它们真正需要的接口。这样可以提高代码的灵活性、可维护性和可扩展性，同时避免了冗余和污染的接口定义。
## 依赖倒置原则（Dependency Inversion Principle，DIP）
> 依赖倒置原则（Dependency Inversion Principle，DIP）是面向对象设计中的一个原则，它提出了一种解耦和减少依赖关系的方式。DIP的核心思想是高层模块不应该依赖于低层模块的具体实现，而应该依赖于抽象。

具体而言，DIP包含以下几个要点：

- 高层模块不应该直接依赖于低层模块：传统的设计方式通常是高层模块直接依赖于低层模块的具体实现，这样会导致高层模块与低层模块之间存在紧密的耦合关系。

- 高层模块和低层模块都应该依赖于抽象：为了解决上述问题，DIP提出，高层模块和低层模块都应该依赖于抽象。抽象可以是接口、抽象类或者其他形式的抽象定义。

- 抽象不应该依赖于具体实现：抽象定义应该是稳定的，不应该依赖于具体实现细节。这样可以确保高层模块对于低层模块的变化是透明的，不需要修改高层模块的代码。

通过遵循DIP，可以实现以下好处：

- 降低耦合度：通过依赖于抽象而不是具体实现，模块之间的耦合度大大降低。这样，当低层模块发生变化时，高层模块不需要做出修改，只需要调整依赖关系即可。

- 提高可测试性：依赖倒置原则有助于模块的解耦和抽象，使得模块的测试变得更加容易。通过使用抽象来模拟依赖关系，可以更方便地进行单元测试和模块测试。

- 支持可扩展性：由于依赖关系是通过抽象定义的，因此系统更容易扩展和变化。可以通过引入新的实现类来扩展系统的功能，而不需要修改现有的高层模块。
### 反例代码
```
public class EmailSender {
    public void sendEmail(String message) {
        // 发送电子邮件的具体实现
    }
}

public class NotificationService {
    private EmailSender emailSender;
    
    public NotificationService() {
        this.emailSender = new EmailSender();
    }
    
    public void sendNotification(String message) {
        emailSender.sendEmail(message);
    }
}
```
在上述代码中，NotificationService 类直接依赖于具体的 EmailSender 类，违反了依赖倒置原则。原因如下：

- 高层模块依赖于低层模块的具体实现：NotificationService 类在构造函数中实例化了具体的 EmailSender 类。这导致高层模块（NotificationService）直接依赖于低层模块（EmailSender）的具体实现。

- 高层模块和低层模块之间的紧耦合关系：由于 NotificationService 类直接依赖于 EmailSender 类，两者之间存在紧密的耦合关系。这意味着如果要更改或扩展通知服务的方式（例如，使用短信发送通知），需要修改 NotificationService 类的代码。
### 建议代码
```
public interface MessageSender {
    void sendMessage(String message);
}

public class EmailSender implements MessageSender {
    public void sendMessage(String message) {
        // 发送电子邮件的具体实现
    }
}

public class NotificationService {
    private MessageSender messageSender;
    
    public NotificationService(MessageSender messageSender) {
        this.messageSender = messageSender;
    }
    
    public void sendNotification(String message) {
        messageSender.sendMessage(message);
    }
}
```
<image src="https://github.com/mvilplss/note/blob/master/image/dip.png?raw=true" alt="依赖倒置说明图"></image>

在上述改进的代码中，我们首先定义了一个抽象的 MessageSender 接口，表示消息发送者的行为。然后，EmailSender 类实现了 MessageSender 接口，负责具体的电子邮件发送操作。

在 NotificationService 类中，通过构造函数依赖注入的方式传入了一个 MessageSender 对象。这样，NotificationService 不再依赖于具体的 EmailSender 类，而是依赖于抽象的 MessageSender 接口。

通过这样的设计，我们遵循了依赖倒置原则。高层模块（NotificationService）不再直接依赖于低层模块（EmailSender）的具体实现，而是依赖于抽象的 MessageSender 接口。这样可以实现以下好处：

- 降低耦合度：NotificationService 类不再依赖于具体的 EmailSender 类，使得两者之间的耦合关系降低。这样，在需要更改或扩展消息发送方式时，只需要提供新的实现类并注入到 NotificationService 中即可，而不需要修改 NotificationService 的代码。

- 提高可测试性：由于 NotificationService 依赖于抽象的 MessageSender 接口，可以轻松地使用模拟对象或桩对象来进行单元测试。这样可以更方便地测试 NotificationService 类的逻辑，而不需要实际发送电子邮件。

需要注意的是，DIP并不是要求完全消除依赖关系，而是通过抽象将依赖关系反转，使得高层模块和低层模块之间的依赖关系更加稳定、灵活和可扩展。通过合理地定义抽象和依赖关系，可以实现代码的解耦和高内聚，提高系统的可维护性和可扩展性。

# 总结和建议
- 单一职责原则（SRP）：一个类或模块应该有且只有一个引起它变化的原因。每个类应该专注于单一的责任。
- 开放封闭原则（OCP）：软件实体（类、模块、函数等）应该对扩展是开放的，而对修改是封闭的。通过抽象和接口定义，实现模块的可扩展性和可重用性。
- 里氏替换原则（LSP）：子类应该能够替换父类并且不影响程序的正确性。即子类必须遵守父类定义的行为约定。
- 接口隔离原则（ISP）：多个特定客户端接口优于一个通用接口。接口应该被细化为具体的角色和职责，避免冗余和不必要的依赖。
- 依赖倒置原则（DIP）：高层模块不应该依赖低层模块，二者都应该依赖于抽象。抽象不应该依赖于具体实现细节，而是应该依赖于更抽象的接口。

这些原则的核心思想是通过合理的分离职责、模块化设计、抽象和接口定义来提高代码的灵活性、可维护性和可扩展性。它们共同促进了松耦合、高内聚和可测试性的软件架构。通过遵循这些原则，开发人员可以设计出更优雅、可维护和可扩展的软件系统。

SOLID的设计原则可以在开发过程中指导我们写出高质量的代码，但是切记不要为了遵循原则而遵循原则，否则可能适得其反；
> 毛泽东1930年5月在《反对本本主义》中提出的一个概念。毛泽东认为，在中国开展无产阶级革命，不讲究中国实际情况，生搬硬套马克思主义，就是本本主义（教条主义）。

# 相关资料
- 《架构整洁之道》8.7 https://book.douban.com/subject/30333919/
- 《设计模式：可复用面向对象软件的基础》9.4 https://book.douban.com/subject/34262305/

# 结束语
理解和使用SOLID设计原则是一个持续学习和提升的过程，需要在项目开发过程中不断的学习和提高。避免我们像开头提到的`庞然大物`，完全的跳出了三界外，不在五个规则中；😓

另外还有一些软件开发原则，感兴趣的同学也可以了解下：
- KISS（Keep It Simple, Stupid）
- DRY（Don't Repeat Yourself）
- ETC（Easier To Change）
