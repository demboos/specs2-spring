package org.specs2.spring.web

import org.springframework.mock.web.{MockHttpServletResponse, MockHttpServletRequest}
import org.springframework.web.servlet.ModelAndView

/**
 * @author janmachacek
 */
class WebObject[B](val request: MockHttpServletRequest,
                 val response: MockHttpServletResponse,
                 val modelAndView: ModelAndView,
                 val body: WebObjectBody[B]) {

  def model = new Model(modelAndView.getModel)

  def << (selector: String, value: String) = {
    new WebObject(request, response, modelAndView, body)
  }

  def >> (selector: String) = {
    ""
  }

  class Model(modelMap: java.util.Map[String, AnyRef]) {

    def apply[T](attributeName: String) = {
      modelMap.get(attributeName).asInstanceOf[T]
    }

    def apply[T <: AnyRef](attributeType: Class[T]): T = {
      val i = modelMap.entrySet().iterator()
      while (i.hasNext) {
        val e = i.next
        if (e.getValue != null && e.getValue.getClass == attributeType) {
          return e.getValue.asInstanceOf[T]
        }
      }
      
      throw new RuntimeException("No element type " + attributeType + " found in the model.")
    }
  }

}

abstract class WebObjectBody[+B](val payload: B) {

  def <<[R >: B](selector: String, value: String): WebObjectBody[R]

  def >>[R](selector: String): Option[R]

  def >>![R](selector: String): R

}
