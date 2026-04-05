export class SchedulerService {
  constructor(taskService, bridge) {
    this.taskService = taskService;
    this.bridge = bridge;
  }

  async tick(now = new Date()) {
    return this.taskService.processDueReminders(now, this.bridge);
  }
}
