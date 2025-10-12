import {ScrollArea} from "@/components/ui/scroll-area.tsx";

type DesktopSidebarProps = {sidebar: any[]}

export function DesktopSidebar({sidebar}: Required<Pick<DesktopSidebarProps, "sidebar">>) {
    return (
        <aside className="hidden md:block w-1/4 shrink-0 border-r bg-white">
            <ScrollArea className="h-[calc(100vh-3.5rem)]">
                <nav className="p-3 space-y-1">
                    {sidebar.map((item, i) => (
                        <a
                            key={i}
                            href={item.href || "#"}
                            className="flex items-center gap-3 rounded-lg px-3 py-2 text-sm text-foreground hover:bg-accent"
                        >
                            {item.icon}
                            <span>{item.label}</span>
                        </a>
                    ))}
                </nav>
            </ScrollArea>
        </aside>
    )
}