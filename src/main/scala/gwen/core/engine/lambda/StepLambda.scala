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

package gwen.core.engine.lambda

import gwen.core.engine.EvalContext
import gwen.core.engine.EvalRules
import gwen.core.model.Identifiable
import gwen.core.model.gherkin.Step

import com.typesafe.scalalogging.LazyLogging

/**
  * Base class for all step lambdas.
  */
abstract class StepLambda[T <: EvalContext, U]() extends EvalRules with LazyLogging {

  /**
    * The operation to apply.
    *
    * @param parent the calling node
    * @param step the step to apply the operation to
    * @param ctx the evaluation context
    */
  def apply(parent: Identifiable, step: Step, ctx: T): U

}
