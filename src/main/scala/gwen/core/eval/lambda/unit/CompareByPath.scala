/*
 * Copyright 2021 Branko Juric, Brady Wood
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package gwen.core.eval.lambda.unit

import gwen.core.eval.ComparisonOperator
import gwen.core.eval.EvalContext
import gwen.core.eval.binding.BindingType
import gwen.core.eval.lambda.UnitStep
import gwen.core.eval.support.XMLNodeType
import gwen.core.node.GwenNode
import gwen.core.node.gherkin.Step
import gwen.core.behavior.BehaviorType

import scala.util.Success
import scala.util.Failure

class CompareByPath[T <: EvalContext](source: String, pathType: BindingType.Value, path: String, expression: String, operator: ComparisonOperator.Value, negate: Boolean) extends UnitStep[T] {

  override def apply(parent: GwenNode, step: Step, ctx: T): Step = {
    step tap { _ =>
      checkStepRules(step, BehaviorType.Assertion, ctx)
      val expected = ctx.parseExpression(operator, expression)
      ctx.perform {
        val src = ctx.scopes.get(source)
        val actual = pathType match {
          case BindingType.`json path` => ctx.evaluateJsonPath(path, src)
          case BindingType.xpath => ctx.evaluateXPath(
            path, src, XMLNodeType.text)
        }
        val result = ctx.compare(s"$source at $pathType '$path'", expected, actual, operator, negate)
        val op = {
          if (operator == ComparisonOperator.`match template file`) {
            ComparisonOperator.`match template` 
          } else {
            operator
          }
        }
        result match {
          case Success(assertion) =>
            assert(assertion, s"Expected $source at $pathType '$path' to ${if(negate) "not " else ""}$op '$expected' but got '$actual'")
          case Failure(error) =>
            assert(assertion = false, error.getMessage)
        }
      }
    }
  }

}
