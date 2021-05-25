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

package gwen.core.engine.lambda.composite

import gwen.core._
import gwen.core.Formatting.DurationFormatter
import gwen.core.engine.EvalContext
import gwen.core.engine.EvalEngine
import gwen.core.engine.binding.JavaScriptBinding
import gwen.core.engine.lambda.CompositeStep
import gwen.core.model._
import gwen.core.model.node.Scenario
import gwen.core.model.node.Step
import gwen.core.model.node.Tag

import scala.concurrent.duration.Duration

class Repeat[T <: EvalContext](doStep: String, operation: String, condition: String, delay: Duration, timeout: Duration, engine: EvalEngine[T]) extends CompositeStep[T] {

  override def apply(parent: Identifiable, step: Step, ctx: T): Step = {
    assert(delay.gteq(Duration.Zero), "delay cannot be less than zero")
    assert(timeout.gt(Duration.Zero), "timeout must be greater than zero")
    assert(timeout.gteq(delay), "timeout cannot be less than or equal to delay")
    val operationTag = Tag(if (operation == "until") ReservedTags.Until else ReservedTags.While)
    val tags = List(Tag(ReservedTags.Synthetic), operationTag, Tag(ReservedTags.StepDef))
    val preCondStepDef = Scenario(None, tags, operationTag.name, condition, Nil, Nil, None, Nil, Nil)
    var condSteps: List[Step] = Nil
    var evaluatedStep = step
    val start = System.nanoTime()
    ctx.perform {
      var iteration = 0
      try {
        ctx.waitUntil(timeout.toSeconds.toInt, s"trying to repeat: ${step.name}") {
          iteration = iteration + 1
          ctx.topScope.set("iteration number", iteration.toString)
          val preStep = step.copy(withKeyword = if(iteration == 1) step.keyword else StepKeyword.And.toString, withName = doStep)
          operation match {
            case "until" =>
              logger.info(s"repeat-until $condition: iteration $iteration")
              if (condSteps.isEmpty) {
                engine.beforeStepDef(step, preCondStepDef, ctx.scopes)
              }
              val iterationStep = engine.evaluateStep(preCondStepDef, preStep, ctx)
              condSteps = iterationStep :: condSteps
              iterationStep.evalStatus match {
                case Failed(_, e) => throw e
                case _ =>
                  val javascript = ctx.interpolate(ctx.scopes.get(JavaScriptBinding.key(condition)))
                  ctx.evaluateJSPredicate(javascript) tap { result =>
                    if (!result) {
                      logger.info(s"repeat-until $condition: not complete, will repeat ${if (delay.gt(Duration.Zero)) s"in ${DurationFormatter.format(delay)}" else "now"}")
                      if (delay.gt(Duration.Zero)) Thread.sleep(delay.toMillis)
                    } else {
                      logger.info(s"repeat-until $condition: completed")
                    }
                  }
              }
            case "while" =>
              val javascript = ctx.interpolate(ctx.scopes.get(JavaScriptBinding.key(condition)))
              val result = ctx.evaluateJSPredicate(javascript)
              if (result) {
                logger.info(s"repeat-while $condition: iteration $iteration")
                if (condSteps.isEmpty) {
                  engine.beforeStepDef(step, preCondStepDef, ctx.scopes)
                }
                val iterationStep = engine.evaluateStep(preCondStepDef, preStep, ctx)
                condSteps = iterationStep :: condSteps
                iterationStep.evalStatus match {
                  case Failed(_, e) => throw e
                  case _ =>
                    logger.info(s"repeat-while $condition: not complete, will repeat ${if (delay.gt(Duration.Zero)) s"in ${DurationFormatter.format(delay)}" else "now"}")
                    if (delay.gt(Duration.Zero)) Thread.sleep(delay.toMillis)
                }
              } else {
                logger.info(s"repeat-while $condition: completed")
              }
              !result
          }
        }
      } catch {
        case e: Throwable =>
          logger.error(e.getMessage)
          val nanos = System.nanoTime() - start
          val durationNanos = {
            if (nanos > timeout.toNanos) timeout.toNanos
            else nanos
          }
          evaluatedStep = step.copy(withEvalStatus = Failed(durationNanos, Errors.stepError(step, e)))
      } finally {
        ctx.topScope.set("iteration number", null)
      }
    } getOrElse {
      try {
        operation match {
          case "until" =>
            evaluatedStep = engine.evaluateStep(step, step.copy(withName = doStep), ctx)
            ctx.scopes.get(JavaScriptBinding.key(condition))
          case _ =>
            ctx.scopes.get(JavaScriptBinding.key(condition))
            evaluatedStep = engine.evaluateStep(step, step.copy(withName = doStep), ctx)
        }
      } catch {
        case _: Throwable => 
          // ignore in dry run mode
      }
    }
    if (condSteps.nonEmpty) {
      val steps = evaluatedStep.evalStatus match {
        case Failed(nanos, error) if (EvalStatus(condSteps.map(_.evalStatus)).status == StatusKeyword.Passed) => 
          val preStep = condSteps.head.copy(withKeyword = StepKeyword.And.toString, withName = doStep)
          engine.beforeStep(preCondStepDef, preStep, ctx.scopes)
          val fStep = engine.finaliseStep(
            preStep.copy(
              withEvalStatus = Failed(nanos - condSteps.map(_.evalStatus.nanos).sum, error),
              withStepDef = None
            ),
            ctx
          )
          engine.afterStep(fStep, ctx.scopes)
          fStep :: condSteps
        case _ => 
          condSteps
          
      }
      val condStepDef = preCondStepDef.copy(withSteps = steps.reverse)
      engine.afterStepDef(condStepDef, ctx.scopes)
      evaluatedStep.copy(
        withEvalStatus = condStepDef.evalStatus,
        withStepDef = Some(condStepDef),
      )
    } else {
      evaluatedStep
    }
  }

}
