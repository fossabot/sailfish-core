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

import { h } from 'preact';
import UserTable from '../../models/UserTable';
import Action, { ActionNodeType } from '../../models/Action';
import { RecoverableExpandablePanel } from '../ExpandablePanel';
import { CustomTable } from './CustomTable';

interface UserTableCardProps {
    table: UserTable;
    parent: Action;
    onExpand: () => void;
}

const UserTableCard = ({ parent, table, onExpand }: UserTableCardProps) => {
    const stateKey = parent.id + '-user-table-' + parent.subNodes
        .filter(node => node.actionNodeType === ActionNodeType.TABLE)
        .indexOf(table).toString();

    return (
        <div class="ac-body__table">
            <RecoverableExpandablePanel
                stateKey={stateKey}
                onExpand={onExpand}>
                <div class="ac-body__item-title">{table.name || "Custom table"}</div>
                <CustomTable
                    content={table.content} />
            </RecoverableExpandablePanel>
        </div>
    )
}

export default UserTableCard;
