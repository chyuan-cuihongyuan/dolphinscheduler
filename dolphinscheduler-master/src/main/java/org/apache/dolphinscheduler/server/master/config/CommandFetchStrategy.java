/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.dolphinscheduler.server.master.config;

import lombok.Data;

import org.springframework.validation.Errors;

/**
 * 命令获取策略配置
 * <p>包含策略类型和具体策略配置，用于控制Master节点从数据库获取任务的策略
 */
@Data
public class CommandFetchStrategy {

    /** 策略类型（默认基于ID槽位） */
    private CommandFetchStrategyType type = CommandFetchStrategyType.ID_SLOT_BASED;
    /** 策略具体配置参数 */
    private CommandFetchConfig config = new IdSlotBasedFetchConfig();

    /**
     * 配置参数校验
     * @param errors Spring验证错误收集对象
     */
    public void validate(Errors errors) {
        config.validate(errors);
    }
    /**
     * 命令获取策略类型枚举
     */
    public enum CommandFetchStrategyType {
        /** 基于ID槽位的分页获取策略 */
        ID_SLOT_BASED
    }

    /**
     * 策略配置接口
     */
    public interface CommandFetchConfig {

        void validate(Errors errors);

    }

    /**
     * ID槽位获取策略配置
     */
    @Data
    public static class IdSlotBasedFetchConfig implements CommandFetchConfig {

        /** ID增长步长（默认1） */
        private int idStep = 1;
        /** 单次获取命令数量（默认10条） */
        private int fetchSize = 10;

        /**
         * 参数校验规则：
         * 1. idStep必须大于0
         * 2. fetchSize必须大于0
         */
        @Override
        public void validate(Errors errors) {
            if (idStep <= 0) {
                errors.rejectValue("step", null, "step must be greater than 0（步长必须大于0）");
            }
            if (fetchSize <= 0) {
                errors.rejectValue("fetchSize", null, "fetchSize must be greater than 0（fetchSize必须大于0）");
            }
        }
    }

}
