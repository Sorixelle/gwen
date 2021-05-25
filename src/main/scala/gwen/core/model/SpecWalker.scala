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

package gwen.core.model

import gwen.core.model.node._

/**
  * Walks the nodes of a specification and accumuates a result of type T.
  */
abstract class SpecWalker[T] {

  // overridable callbacks
  def onSpec(parent: Identifiable, spec: Spec, acc: T): T = acc
  def onFeature(parent: Identifiable, feature: Feature, acc: T): T = acc
  def onBackground(parent: Identifiable, background: Background, acc: T): T = acc
  def onRule(parent: Identifiable, rule: Rule, acc: T): T = acc
  def onScenario(parent: Identifiable, scenario: Scenario, acc: T): T = acc
  def onExamples(parent: Identifiable, examples: Examples, acc: T): T = acc
  def onStep(parent: Identifiable, step: Step, acc: T): T = acc
  
  /**
    * Recursively walks all nodes in the tree.  
    *
    * @param parent the parent
    * @param node the node to walk
    * @param zero the initial accumulator value
    */
  def walk(parent: Identifiable, node: SpecNode, zero: T): T = {
    var acc = zero
    node match {
      case spec: Spec =>
        acc = onSpec(parent, spec, acc)
        acc = onFeature(spec, spec.feature, acc)
        spec.background foreach { background => 
          acc = walk(spec.feature, background, acc)
        }
        spec.scenarios foreach { scenario => 
          acc = walk(spec.feature, scenario, acc)
        }
        spec.rules foreach { rule => 
          acc = walk(spec.feature, rule, acc)
        }
        acc
      case background: Background =>
        acc = onBackground(parent, background, acc)
        background.steps foreach { step => 
          acc = walk(background, step, acc)
        }
        acc
      case rule: Rule =>
        acc = onRule(parent, rule, acc)
        rule.background foreach { background => 
          acc = walk(rule, background, acc)
        }
        rule.scenarios foreach { scenario => 
          acc = walk(rule, scenario, acc)
        }
        acc
      case scenario: Scenario =>
        scenario.background foreach { background =>
          acc = walk(parent, background, acc)
        }
        acc = onScenario(parent, scenario, acc)
        scenario.steps foreach { step => 
          acc = walk(scenario, step, acc)
        }
        scenario.examples foreach { exs =>
          acc = walk(scenario, exs, acc)
        }
        acc
      case examples: Examples  => 
        acc = onExamples(parent, examples, acc)
        examples.scenarios foreach { scenario => 
          acc = walk(examples, scenario, acc)
        }
        acc
      case step: Step =>
        onStep(parent, step, acc)
    }
  }
  
}
