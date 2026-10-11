import { createApp } from 'vue'
import 'vant/lib/index.css'
import './style.css'
import App from './App.vue'
import { Button, Card, Tabbar, TabbarItem, NavBar, NoticeBar, Field, Popup, Picker, Icon, Collapse, CollapseItem, Loading, Cell, CellGroup, Switch, SwipeCell, Empty, Search, List, Dialog } from 'vant'
import router from './router'

const app = createApp(App)
app.use(Button)
app.use(Card)
app.use(router)
app.use(Tabbar)
app.use(TabbarItem)
app.use(NavBar)
app.use(NoticeBar)
app.use(Field)
app.use(Popup)
app.use(Picker)
app.use(Icon)
app.use(Collapse)
app.use(CollapseItem)
app.use(Loading)
app.use(Cell)
app.use(CellGroup)
app.use(Switch)
app.use(SwipeCell)
app.use(Empty)
app.use(Search)
app.use(List)
app.use(Dialog)
app.mount('#app')
