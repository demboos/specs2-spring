#Specs2 Spring [![Build Status](https://travis-ci.org/eigengo/specs2-spring.png?branch=master)](https://travis-ci.org/eigengo/specs2-spring)
Specs2 Extension to simplify integration testing with Spring.

> More details at blogs at http://www.cakesolutions.net/teamblogs and http://www.cakesolutions.org

Most Spring enterprise applications use some DataSources, TransactionManagers and other JEE beasts. In
addition to having the beans injected into our specs, we would like to use Specs2 to perform the necessary
integration testing, but we don't really want to create separate application context files for the tests. 

Instead, we would like to set up the JNDI environment for the test code and use the same application context files
for both testing and production. This is where this project helps: the annotations on our test classes specify
the JNDI environment we wish to build for the test. 

Verba docent, exempla trahunt, so I'll start you off with a simple sample. Let there be:
```scala
@Component
class SomeComponent @Autowired()(private val hibernateTemplate: HibernateTemplate) {

  @Transactional
  def generate(count: Int) {
    for (c <- 0 until count) {
      val rider = new Rider()
      rider.setName("Rider #" + c)
      this.hibernateTemplate.saveOrUpdate(rider)
    }
  }

}
```
To get this running, we give the ``META-INF/spring/module-context.xml`` configuration file:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="..."
	   xsi:schemaLocation="...">

	<context:component-scan base-package="org.specs2.springexample"/>

	*<jee:jndi-lookup id="dataSource" jndi-name="java:comp/env/jdbc/test" expected-type="javax.sql.DataSource"/>*
	*<tx:jta-transaction-manager />*
	<tx:annotation-driven />

	<bean id="sessionFactory" class="org.springframework.orm.hibernate3.annotation.AnnotationSessionFactoryBean">
		<property name="dataSource" ref="dataSource"/>
		<property name="packagesToScan">
			<list>
				<value>org.specs2.springexample</value>
			</list>
		</property>
	</bean>

	<bean id="hibernateTemplate" class="org.springframework.orm.hibernate3.HibernateTemplate">
		<property name="sessionFactory" ref="sessionFactory"/>
	</bean>

</beans>
```
This context file is the same for both tests and for production. The "variable" items (``DataSource`` and ``TransactionManager``) beans are looked up from JNDI.

To the test, then. We have simply
```scala
@TransactionManager(name = "java:comp/TransactionManager")
@DataSource(name = "java:comp/env/jdbc/test", driverClass = classOf[JDBCDriver], url = "jdbc:hsqldb:mem:test")
@Transactional
@TransactionConfiguration(defaultRollback = true)
@ContextConfiguration(Array("classpath*:/META-INF/spring/module-context.xml"))
class SomeComponentSpec extends org.specs2.spring.Specification {
  @Autowired var someComponent: SomeComponent = _
  @Autowired var hibernateTemplate: HibernateTemplate = _

  "The rider generation mechanism" in {
    "generate 100 riders " ! generate(100)
  }

  def generate(count: Int) = {
    this.someComponent.generate(count)
    this.hibernateTemplate.loadAll(classOf[Rider]) must have size (count)
  }

}
```
If I wanted to have another integration test (perhaps testing another class), I would write:
```scala
@TransactionManager(name = "java:comp/TransactionManager")
@DataSource(name = "java:comp/env/jdbc/test", driverClass = classOf[JDBCDriver], url = "jdbc:hsqldb:mem:test")
@Transactional
@TransactionConfiguration(defaultRollback = true)
@ContextConfiguration(Array("classpath*:/META-INF/spring/module-context.xml"))
class SomeOtherComponentSpec extends org.specs2.spring.Specification {
  @Autowired var someOtherComponent: SomeOtherComponent = _

  "Another component" in {
    "do something clever " ! doSomethingClever
  }


  def doSomethingClever = {
    success
  }

}
```
At this point, you may notice the duplication in the annotations. The Specs2 spring extension allows you to "merge" these annotations into one annotation that you can use throughout your tests. You can therefore have:
```scala
@TransactionManager(name = "java:comp/TransactionManager")
@DataSource(name = "java:comp/env/jdbc/test", driverClass = classOf[JDBCDriver], url = "jdbc:hsqldb:mem:test")
@Transactional
@TransactionConfiguration(defaultRollback = true)
@ContextConfiguration(Array("classpath*:/META-INF/spring/module-context.xml"))
public @interface IntegrationTest {
}
```
and modify the specs to just
```scala
@IntegrationTest
class SomeComponentSpec extends Specification { ... }

@IntegrationTest
class SomeComponentSpec extends Specification { ... }
```
To set up multiple ``DataSource``s, ``MailSession``s, ... as well as JMS queues and topics, you can use the Jndi annotation like this:
```java
@Jndi(
		dataSources = {
				@DataSource(name = "java:comp/env/jdbc/test",
					driverClass = JDBCDriver.class, url = "jdbc:hsqldb:mem:test"),
				@DataSource(name = "java:comp/env/jdbc/external",
					driverClass = JDBCDriver.class, url = "jdbc:hsqldb:mem:external")
		},
		mailSessions = @MailSession(name = "java:comp/env/mail/foo"),
		transactionManager = @TransactionManager(name = "java:comp/TransactionManager"),
		jms = @Jms(
				connectionFactoryName = "java:comp/env/jms/connectionFactory",
				queues = {@JmsQueue(name = "java:comp/env/jms/requests"), 
						  @JmsQueue(name = "java:comp/env/jms/responses")},
				topics = {@JmsTopic(name = "java:comp/env/jms/cacheFlush"), 
						  @JmsTopic(name = "java:comp/env/jms/ruleUpdate")}
		),
		workManagers = @WorkManager(name = "java:comp/env/work/WorkManager", kind = WorkManager.Kind.CommonJ)
)
@Transactional
@TransactionConfiguration(defaultRollback = true)
@ContextConfiguration("classpath*:/META-INF/spring/module-context.xml")
@Retention(RetentionPolicy.RUNTIME)
public @interface IntegrationTest {
}
```
