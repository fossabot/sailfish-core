/******************************************************************************
* Copyright 2009-2019 Exactpro (Exactpro Systems Limited)
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
******************************************************************************/

import { h } from "preact";
import Verification from '../../models/Verification';
import { StatusType } from "../../models/Status";
import { createSelector } from '../../helpers/styleCreators';
import { RecoverableExpandablePanel } from "../ExpandablePanel";
import { RecoverableVerificationTable } from "./VerificationTable";
import '../../styles/action.scss';

interface VerificactionCardProps {
    verification: Verification;
    isSelected: boolean;
    isTransparent: boolean;
    parentActionId: number;
    onSelect: (msgId: number, status: StatusType) => any;
    onExpand: () => void;
}

const VerificationCard = ({ verification, onSelect, isSelected, isTransparent, parentActionId, onExpand }: VerificactionCardProps) => {

    const { status, messageId, entries, name } = verification;

    const className = createSelector(
        "ac-body__verification",
        status && status.status,
        isSelected ? "selected" : null,
        isTransparent && !isSelected ? "transparent" : null
    );

    return (
        <div class="action-card">
            <div class={className}
                onClick={e => {
                    onSelect(messageId, status.status);
                    // here we cancel handling by parent divs
                    e.cancelBubble = true;
                }}>
                <RecoverableExpandablePanel
                    stateKey={parentActionId + '-' + messageId}
                    onExpand={onExpand}>
                    <div class="ac-body__verification-title">{"Verification — " + name + " — " + status.status}</div>
                    <RecoverableVerificationTable 
                        stateKey={parentActionId + '-' + messageId + '-nodes'}
                        params={entries} 
                        status={status.status}
                        onExpand={onExpand}/>
                </RecoverableExpandablePanel>
            </div>
        </div>
    )
}

export default VerificationCard;
