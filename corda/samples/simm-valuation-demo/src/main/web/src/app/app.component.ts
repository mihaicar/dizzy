import { Component, ViewEncapsulation } from '@angular/core';
import { ROUTER_DIRECTIVES } from '@angular/router';
import { CORE_DIRECTIVES, NgClass, NgIf } from '@angular/common';
import { SELECT_DIRECTIVES } from 'ng2-select/ng2-select';
import * as moment from 'moment';
import { HttpWrapperService } from './http-wrapper.service';

@Component({
  moduleId: module.id,
  selector: 'app-root',
  templateUrl: 'app.component.html',
  styleUrls: ['app.component.css', '../vendor/ng2-select/components/css/ng2-select.css'],
  directives: [
    ROUTER_DIRECTIVES,
    NgClass,
    SELECT_DIRECTIVES
  ],
  encapsulation: ViewEncapsulation.None, // allow external CSS
  providers: [HttpWrapperService] // don't declare in children, so that it's a "singleton"
})

export class AppComponent {

  constructor(private httpWrapperService: HttpWrapperService) {}

  public whoAmI: string; // name
  public counterParty: string; // id
  public counterParties: Array < any > = [];

  public selected(value: any): void {};

  public refreshValue(value: any): void {
    this.counterparty = this.httpWrapperService.setCounterparty(value.id);
  }

  private counterparty: any = null;

  ngOnInit() {
    this.httpWrapperService.getAbsolute("whoami").toPromise().then((data) => {
      this.whoAmI = data.self.text;
      this.counterParties = data.counterparties;
      if (this.counterParties.length == 0) {
        console.log("/whoami is returning no counterparties, the whole app won't run", data);
      }
    }).catch((error) => {
      console.log("Error loading who am i (this is really bad, the whole app will not work)", error);
    });
  }
}
