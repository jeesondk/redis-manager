import * as React from "react"
import {DesktopTopbar} from "@/components/DesktopTopbar.tsx";
import {DesktopSidebar} from "@/components/DesktopSidebar.tsx";

// Types
interface Crumb { label: string; href?: string }
interface MenuItem { label: string; href?: string; icon?: React.ReactNode }

interface AppLayoutProps {
    userName?: string
    userAvatarUrl?: string
    breadcrumbs?: Crumb[]
    sidebar?: MenuItem[]
    children?: React.ReactNode
    logo?: React.ReactNode
}

// Default data for quick preview
const defaultCrumbs: Crumb[] = [
    { label: "Home", href: "/" }
]


/*function MobileHeader({ logo, sidebar }: Pick<AppLayoutProps, "logo" | "sidebar">) {
    return (
        <div className="md:hidden fixed top-0 left-0 right-0 z-40 h-12 border-b bg-white px-3">
            <div className="flex h-full items-center justify-between">
                <div className="flex items-center gap-2">
                    {logo}
                </div>
                <Sheet>
                    <SheetTrigger asChild>
                        <Button variant="ghost" size="icon" aria-label="Open menu">
                            <Menu className="h-5 w-5" />
                        </Button>
                    </SheetTrigger>
                    <SheetContent side="right" className="p-0">
                        <SheetHeader className="px-4 py-3">
                            <SheetTitle>Menu</SheetTitle>
                        </SheetHeader>
                        <Separator />
                        <ScrollArea className="h-[calc(100vh-5rem)]">
                            <nav className="p-2">
                                {sidebar?.map((item, i) => (
                                    <a
                                        key={i}
                                        href={item.href || "#"}
                                        className="flex items-center gap-3 rounded-md px-3 py-2 text-sm hover:bg-accent"
                                    >
                                        {item.icon}
                                        <span>{item.label}</span>
                                    </a>
                                ))}
                            </nav>
                        </ScrollArea>
                    </SheetContent>
                </Sheet>
            </div>
        </div>
    )
}*/

export default function AppLayout(props: AppLayoutProps) {
    const userName = props.userName ?? ""
    const crumbs = props.breadcrumbs ?? defaultCrumbs
    const logo = props.logo ?? (
        <a href="/" className="flex items-center gap-2">
            <div className="h-6 w-6 rounded bg-primary" />
            <span className="text-sm font-semibold tracking-tight hidden xs:block">YourLogo</span>
        </a>
    )

    return (
        <div className="h-screen w-full bg-slate-50 text-slate-900 flex flex-col overflow-hidden">
            {/* Mobile header (burger)
            <MobileHeader logo={logo} sidebar={sidebar}/>
            */}
            {/* Desktop topbar */}
            <DesktopTopbar
                userName={userName}
                userAvatarUrl={props.userAvatarUrl ?? "https://github.com/shadcn.png"}
                breadcrumbs={crumbs}
                logo={logo}
            />

            <div className="mx-auto w-full max-w-[1400px] px-3 md:px-6 min-h-0 flex flex-col">
                {/* Account for fixed mobile header height */}
                <div className="pt-12 md:pt-4 shrink-0" />

                <div className="flex-col-2 md:pb-6 pb-4 h-screen" >


                <div className="flex h-full gap-4 md:gap-6 overflow-hidden">
                    <DesktopSidebar />

                    {/* Main content */}
                    <main>
                        <div className="flex h-full overflow-hidden bg-white">
                            { props.children }
                        </div>
                    </main>
                    </div>
                </div>
            </div>
        </div>
    )
}
