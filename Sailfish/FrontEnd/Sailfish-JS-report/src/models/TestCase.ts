import Action from "./Action";
import Message from "./Message";
import Log from "./Log";
import Status from './Status';

export default interface TestCase {
    name?: string;
    actions: Action[];
    logs: Log[];
    messages: Message[];
    bugs: any[];
    type: string;
    reference?: any;
    order: number;
    outcomes?: any[];
    matrixOrder: number;
    id: string;
    hash: number;
    description: string;
    status: Status;
    startTime: string;
    finishTime: string;
    verifications?: any[];
}