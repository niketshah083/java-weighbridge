import{ja as O,ka as R,ma as V,pa as q,qa as G,ra as s}from"./chunk-TNFEVYQM.js";import{j,l as z,p as P,v as Q}from"./chunk-W3X2JT2K.js";import{Ab as B,Bb as M,Ib as g,Jb as w,Jc as F,Kb as D,Lb as N,Ma as i,N as T,Nb as y,Ob as v,Q as b,S as l,Wb as c,Xb as S,Yb as E,_a as h,cb as C,db as I,eb as r,gc as A,kb as k,na as u,tb as a,ub as f,vb as m,wb as x}from"./chunk-C3GMV2W5.js";var H=`
    .p-tag {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        background: dt('tag.primary.background');
        color: dt('tag.primary.color');
        font-size: dt('tag.font.size');
        font-weight: dt('tag.font.weight');
        padding: dt('tag.padding');
        border-radius: dt('tag.border.radius');
        gap: dt('tag.gap');
    }

    .p-tag-icon {
        font-size: dt('tag.icon.size');
        width: dt('tag.icon.size');
        height: dt('tag.icon.size');
    }

    .p-tag-rounded {
        border-radius: dt('tag.rounded.border.radius');
    }

    .p-tag-success {
        background: dt('tag.success.background');
        color: dt('tag.success.color');
    }

    .p-tag-info {
        background: dt('tag.info.background');
        color: dt('tag.info.color');
    }

    .p-tag-warn {
        background: dt('tag.warn.background');
        color: dt('tag.warn.color');
    }

    .p-tag-danger {
        background: dt('tag.danger.background');
        color: dt('tag.danger.color');
    }

    .p-tag-secondary {
        background: dt('tag.secondary.background');
        color: dt('tag.secondary.color');
    }

    .p-tag-contrast {
        background: dt('tag.contrast.background');
        color: dt('tag.contrast.color');
    }
`;var K=["icon"],L=["*"];function U(t,d){if(t&1&&x(0,"span",4),t&2){let e=g(2);c(e.cx("icon")),a("ngClass",e.icon)("pBind",e.ptm("icon"))}}function W(t,d){if(t&1&&(B(0),r(1,U,1,4,"span",3),M()),t&2){let e=g();i(),a("ngIf",e.icon)}}function X(t,d){}function Y(t,d){t&1&&r(0,X,0,0,"ng-template")}function Z(t,d){if(t&1&&(f(0,"span",2),r(1,Y,1,0,null,5),m()),t&2){let e=g();c(e.cx("icon")),a("pBind",e.ptm("icon")),i(),a("ngTemplateOutlet",e.iconTemplate||e._iconTemplate)}}var tt={root:({instance:t})=>["p-tag p-component",{"p-tag-info":t.severity==="info","p-tag-success":t.severity==="success","p-tag-warn":t.severity==="warn","p-tag-danger":t.severity==="danger","p-tag-secondary":t.severity==="secondary","p-tag-contrast":t.severity==="contrast","p-tag-rounded":t.rounded}],icon:"p-tag-icon",label:"p-tag-label"},$=(()=>{class t extends V{name="tag";style=H;classes=tt;static \u0275fac=(()=>{let e;return function(n){return(e||(e=u(t)))(n||t)}})();static \u0275prov=T({token:t,factory:t.\u0275fac})}return t})();var J=new b("TAG_INSTANCE"),Ct=(()=>{class t extends G{$pcTag=l(J,{optional:!0,skipSelf:!0})??void 0;bindDirectiveInstance=l(s,{self:!0});onAfterViewChecked(){this.bindDirectiveInstance.setAttrs(this.ptms(["host","root"]))}styleClass;severity;value;icon;rounded;iconTemplate;templates;_iconTemplate;_componentStyle=l($);onAfterContentInit(){this.templates?.forEach(e=>{switch(e.getType()){case"icon":this._iconTemplate=e.template;break}})}get dataP(){return this.cn({rounded:this.rounded,[this.severity]:this.severity})}static \u0275fac=(()=>{let e;return function(n){return(e||(e=u(t)))(n||t)}})();static \u0275cmp=h({type:t,selectors:[["p-tag"]],contentQueries:function(o,n,_){if(o&1&&N(_,K,4)(_,O,4),o&2){let p;y(p=v())&&(n.iconTemplate=p.first),y(p=v())&&(n.templates=p)}},hostVars:3,hostBindings:function(o,n){o&2&&(k("data-p",n.dataP),c(n.cn(n.cx("root"),n.styleClass)))},inputs:{styleClass:"styleClass",severity:"severity",value:"value",icon:"icon",rounded:[2,"rounded","rounded",F]},features:[A([$,{provide:J,useExisting:t},{provide:q,useExisting:t}]),C([s]),I],ngContentSelectors:L,decls:5,vars:6,consts:[[4,"ngIf"],[3,"class","pBind",4,"ngIf"],[3,"pBind"],[3,"class","ngClass","pBind",4,"ngIf"],[3,"ngClass","pBind"],[4,"ngTemplateOutlet"]],template:function(o,n){o&1&&(w(),D(0),r(1,W,2,1,"ng-container",0)(2,Z,2,4,"span",1),f(3,"span",2),S(4),m()),o&2&&(i(),a("ngIf",!n.iconTemplate&&!n._iconTemplate),i(),a("ngIf",n.iconTemplate||n._iconTemplate),i(),c(n.cx("label")),a("pBind",n.ptm("label")),i(),E(n.value))},dependencies:[Q,j,z,P,R,s],encapsulation:2,changeDetection:0})}return t})();export{Ct as a};
